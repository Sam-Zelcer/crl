package com.crl.hh.service.osint;

import com.crl.hh.repository.SiteEntityRepository;
import com.crl.hh.repository.models.SiteEntity;
import com.crl.hh.repository.models.enums.ChekTypeForSiteEntity;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.TimeoutException;
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
import java.util.concurrent.*;

@Service
public class OSINTService {

    private static final Logger logger = LoggerFactory.getLogger(OSINTService.class);

    private final SiteEntityRepository siteEntityRepository;
    private final HttpClient httpClient;
    private final ExecutorService executorService;

    public OSINTService(SiteEntityRepository siteEntityRepository) {
        this.executorService = Executors.newFixedThreadPool(4);
        this.siteEntityRepository = siteEntityRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<String> searchByUsername(String username) {
        List<SiteEntity> sites = siteEntityRepository.getSites();
        CompletionService<Optional<String>> cs =  new ExecutorCompletionService<>(executorService);

        for (SiteEntity site : sites) {
            cs.submit(() -> checkSites(site, username));
        }

        List<String> found = new ArrayList<>();
        int tasks = sites.size();

        for (int i = 0; i < tasks; i++) {

            try {
                Future<Optional<String>> completed = cs.poll(30, TimeUnit.SECONDS);

                if (completed == null) {
                    logger.warn("No task completed within timeout iteration {}", i);
                    continue;
                }

                try {
                    completed.get().ifPresent(found::add);
                } catch (ExecutionException e) {
                    logger.warn("Search interrupted", e);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Search interrupted: {}", e.getMessage());
                break;
            }
        }
        return found;
    }

    private Optional<String> checkSites(SiteEntity site, String username) {
        String url = String.format(site.getUrlPattern(), username);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code == 404 || code == 410) {
                logger.debug("HTTP status {} fro {}, skipping", code, url);
                return Optional.empty();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("HTTP check interrupted for {}: {}", url, e.getMessage(), e);
            return Optional.empty();

        } catch (IOException e) {
            logger.debug("HTTP request failed for {}: {}, will fallback for Selenium", url, e.getMessage(), e);
        }

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless=new", "--disable-gpu", "--blink-settings=imagesEnabled=false");
        chromeOptions.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        chromeOptions.addArguments("--remote-allow-origins=*");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(chromeOptions);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            driver.get(url);

            ChekTypeForSiteEntity checkType = Optional.ofNullable(site.getType()).orElse(ChekTypeForSiteEntity.TITLE);
            boolean exists = false;

            if (
                    ChekTypeForSiteEntity.ELEMENT == checkType && site.getElementSelector() != null && !site.getElementSelector().isBlank()
            ) {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(site.getElementSelector())));
                    exists = true;
                } catch (TimeoutException ignored) {}


            } else {
                String title = Optional.ofNullable(driver.getTitle()).orElse("").toLowerCase(Locale.ROOT);
                List<String> notFound = Optional.ofNullable(site.getNotFoundIndicators()).orElse(List.of());
                boolean hasNotFound = notFound.stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .anyMatch(title::contains);
                exists = !hasNotFound;
            }

            return exists ? Optional.of(url) : Optional.empty();
        } catch (WebDriverException e) {
            logger.warn("Selenium error for {}: {}", url, e.getMessage(), e);
            return Optional.empty();
        } finally {
            if (driver != null ) {
                try {
                    driver.quit();
                } catch (Exception ignored) {}
            }
        }
    }
}
