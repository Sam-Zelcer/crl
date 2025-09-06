package com.crl.hh.repository;

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
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotFoundIndicatorsRepository {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${notFoundIndicators.file:classpath:./src/main/resources/static/notFoundIndicators.json}")
    private String notFoundIndicatorsFile;

    @Getter
    private List<String> notFoundIndicators;

    @PostConstruct
    public void init() {
        Resource resource = resourceLoader.getResource(notFoundIndicatorsFile);
        if (!resource.exists()) {
            throw new IllegalStateException("Resource not found: " + notFoundIndicatorsFile);
        }

        try (InputStream is = resource.getInputStream()) {
            List<String> loadedIndicators = objectMapper.readValue(is, new TypeReference<>() {});
            this.notFoundIndicators = new ArrayList<>(loadedIndicators);

        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to load sites file: " + notFoundIndicatorsFile, ioe);
        }
    }
}
