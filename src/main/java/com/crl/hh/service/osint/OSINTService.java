package com.crl.hh.service.osint;

import com.crl.hh.repository.NotFoundIndicatorsRepository;
import com.crl.hh.repository.SiteEntityRepository;
import com.crl.hh.repository.models.SiteEntity;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class OSINTService {

    private static final Logger logger = LoggerFactory.getLogger(OSINTService.class);

    private final SiteEntityRepository siteEntityRepository;
    private final NotFoundIndicatorsRepository notFoundIndicatorsRepository;
    private final HttpClient httpClient;

    public OSINTService(SiteEntityRepository siteEntityRepository, NotFoundIndicatorsRepository notFoundIndicatorsRepository) {
        this.siteEntityRepository = siteEntityRepository;
        this.notFoundIndicatorsRepository = notFoundIndicatorsRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<String> searchByUsername(String username) {
        if (username.isBlank()) {
            logger.error("Missing username");
            return null;
        }

        List<SiteEntity> sites = siteEntityRepository.getSites();
        List<String> notFoundIndicatorsLower = Optional.ofNullable(notFoundIndicatorsRepository.getNotFoundIndicators())
                .orElse(List.of())
                .stream()
                .map(s -> s.toLowerCase(Locale.ROOT).trim())
                .toList();
        List<String> found =  new ArrayList<>();

        Pattern usernamePattern = Pattern.compile(
                "(?<![\\p{L}\\p{Nd}_])" + Pattern.quote(username) + "(?![\\p{L}\\p{Nd}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new", "--disable-gpu", "--blink-settings=imagesEnabled=false");
        chromeOptions.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        chromeOptions.addArguments("--remote-allow-origins=*");

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(chromeOptions);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));

            for (SiteEntity site : sites) {
                String url = String.format(site.getUrlPattern(), username);

//              STATUS CODE CHECK --
                Integer statusCode = getStatusCode(url);
                if (
                        statusCode != null && (statusCode == 404 || statusCode == 410)
                ) {
                    logger.debug("Skipping site {}, HTTP status code {}", url, statusCode);
                    continue;
                }

                boolean isElement = false;
                boolean isTitleHasUsername = false;
                boolean isBodyHasUsername = false;

                try {
                    driver.get(url);

//                  TITLE AND BODY CHECK --
                    String title = Optional.ofNullable(driver.getTitle()).orElse("").toLowerCase(Locale.ROOT);
                    String body = "";
                    try {
                        body = driver.findElement(By.tagName("body")).getText();
                    } catch (Exception ignored) {}

                    String bodyLower = body.toLowerCase(Locale.ROOT);

                    boolean hasNotFindIndicators = notFoundIndicatorsLower.stream()
                            .anyMatch(ind -> title.contains(ind) || bodyLower.contains(ind));

                    if(hasNotFindIndicators) {
                        logger.debug("Detected not-found indicator for {} (title='{}', bodySnippet='{}')",
                                url,
                                title,
                                bodyLower.length() > 160 ? bodyLower.substring(0, 160) : bodyLower);
                        continue;
                    }

//                  USERNAME CHECK
                    isTitleHasUsername = usernamePattern.matcher(title).find();
                    isBodyHasUsername = usernamePattern.matcher(bodyLower).find();

                    if (!isBodyHasUsername) {
                        try {
                            String html = Optional.ofNullable(driver.getPageSource()).orElse("").toLowerCase(Locale.ROOT);
                            isBodyHasUsername = usernamePattern.matcher(html).find();
                        } catch (Exception ignored) {}
                    }

//                  ELEMENT CHECK
                    String selector = Optional.ofNullable(site.getElementSelector()).orElse("").trim();
                    if(!selector.isBlank()) {
                        WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(15));
                        try {
                            webDriverWait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                            isElement = true;
                        } catch (TimeoutException ignored) {}
                    }

                } catch (WebDriverException wbe) {
                    logger.debug("Selenium failed for {}: {}", url, wbe.getMessage());
                }

                System.out.println(url);
                System.out.println(isElement);
                System.out.println(isTitleHasUsername);
                System.out.println(isBodyHasUsername);

                if (
                        (isElement || (isTitleHasUsername && isBodyHasUsername))
                ) {
                    found.add(url);
                }
            }

        } catch (WebDriverException e) {
            logger.error("Webdriver failed for {}: {}", username, e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (WebDriverException ignored) {}
            }
        }

        return found;
    }

    private Integer getStatusCode(String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> response = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (IOException | InterruptedException e) {
            logger.debug("HEAD failed for {}: {}",  url, e.getMessage());

            try {
                HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<Void> response = httpClient.send(get, HttpResponse.BodyHandlers.discarding());
                return response.statusCode();

            } catch (Exception ex) {
                logger.debug("GET failed for {}: {}",  url, ex.getMessage());
                return null;
            }
        }
    }

}