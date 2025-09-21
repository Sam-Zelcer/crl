package com.crl.hh.service.osint;

import com.crl.hh.repository.NotFoundIndicatorsRepository;
import com.crl.hh.repository.SiteEntityRepository;
import com.crl.hh.repository.models.SiteEntity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.*;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class OSINTService {

    private static final Logger logger = LoggerFactory.getLogger(OSINTService.class);

    private final SiteEntityRepository siteEntityRepository;
    private final NotFoundIndicatorsRepository notFoundIndicatorsRepository;
    private final HttpClient httpClient;
    private ExecutorService executorService;
    private ChromeOptions chromeOptions;

    public OSINTService(SiteEntityRepository siteEntityRepository, NotFoundIndicatorsRepository notFoundIndicatorsRepository) {
        this.siteEntityRepository = siteEntityRepository;
        this.notFoundIndicatorsRepository = notFoundIndicatorsRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PostConstruct
    public void init() {
        chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new", "--disable-gpu", "--blink-settings=imagesEnabled=false");
        chromeOptions.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        chromeOptions.addArguments("--remote-allow-origins=*");

        executorService = Executors.newFixedThreadPool(8);
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

        List<CompletableFuture<String>> futures = sites.stream()
                .map(site -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return checkSite(site, username, notFoundIndicatorsLower);
                    } catch (Exception e) {
                        return null;
                    }
                }, executorService))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String checkSite(SiteEntity site, String username, List<String> notFoundIndicatorsLower) {
        String url = String.format(site.getUrlPattern(), username);
        WebDriver driver = null;

//      STATUS CODE CHECK
        Integer statusCode = getStatusCode(url);
        if (statusCode != null && (statusCode == 404 || statusCode == 410)) return null;

        try {
            driver = new ChromeDriver(chromeOptions);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            WebDriverWait webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(15));

            driver.get(url);

//          URL CHECK
            String currentUrl = Optional.ofNullable(driver.getCurrentUrl()).orElse("").toLowerCase(Locale.ROOT);
            if (!currentUrl.equals(url.toLowerCase(Locale.ROOT)) && !currentUrl.contains(username.toLowerCase(Locale.ROOT))) return null;

            try {
//              CUSTOM CHECK FOR INSTAGRAM
                if (site.getName().equals("Instagram")) {
                    webDriverWait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("span")));

                    String instagram = driver.findElement(By.tagName("span")).getText().toLowerCase(Locale.ROOT);
                    if (notFoundIndicatorsLower.stream().anyMatch(instagram::contains)) return null;
                }

//              CUSTOM CHECK FOR TWITCH
                if (site.getName().equals("Twitch")) {
                    webDriverWait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("p")));

                    String twitch = driver.findElement(By.tagName("body")).getText().toLowerCase(Locale.ROOT);
                    if (!twitch.contains(username.toLowerCase(Locale.ROOT))) return null;
                }

//              HAS NOT FOUND INDICATORS CHECK
                webDriverWait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("div")));

                String body = driver.findElement(By.tagName("body")).getText().toLowerCase(Locale.ROOT);
                if (notFoundIndicatorsLower.stream().anyMatch(body::contains)) return null;

            } catch (TimeoutException te) {
                logger.debug("Element not found within timeout for --. {}", site.getName());
            }

//          ELEMENT CHECK
            String selector = Optional.ofNullable(site.getElementSelector()).orElse("").trim();
            if(!selector.isBlank()) {
                try {
                    webDriverWait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                    WebElement element = driver.findElement(By.cssSelector(selector));
                    if (element.isEnabled()) {
                        return url;
                    }

                } catch (TimeoutException ignored) {}
            }

        } catch (WebDriverException wbe) {
            logger.debug("Selenium failed for {}: {}", url, wbe.getMessage());
            return null;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.debug("Error quitting driver for {}: {}", url, e.getMessage());
                }
            }
        }

        if (site.getName().equals("X")) return null;
        return url;
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

    @PreDestroy
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}