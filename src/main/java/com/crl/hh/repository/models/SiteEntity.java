package com.crl.hh.repository.models;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class SiteEntity {

    private String name;
    private String urlPattern;
    @Enumerated(EnumType.STRING)
    private String elementSelector;
    private boolean enabled = true;
}
