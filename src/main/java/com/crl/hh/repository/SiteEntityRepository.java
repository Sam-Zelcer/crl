package com.crl.hh.repository;

import com.crl.hh.repository.models.SiteEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SiteEntityRepository {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${sites.file:classpath:./src/main/resources/static/urlRepository.json}")
    private String sitesFile;

    @Getter
    private List<SiteEntity> sites = Collections.emptyList();

    @PostConstruct
    public void init() {
        Resource resource = resourceLoader.getResource(sitesFile);
        if (!resource.exists()) {
            throw new IllegalStateException("Sites file not found: " + sitesFile);
        }

        try (InputStream is = resource.getInputStream()) {
            List<SiteEntity> loadedSitesEntity = objectMapper.readValue(is, new TypeReference<>() {});

            this.sites = loadedSitesEntity.stream()
                    .filter(e -> e.getUrlPattern() != null && !e.getUrlPattern().isBlank())
                    .filter(SiteEntity::isEnabled)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load sites file: " + sitesFile, e);
        }
    }

}
