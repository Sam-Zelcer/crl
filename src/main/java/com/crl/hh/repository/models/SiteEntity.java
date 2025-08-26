package com.crl.hh.repository.models;

import com.crl.hh.repository.models.enums.ChekTypeForSiteEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class SiteEntity {

    private String name;
    private String urlPattern;
    @Enumerated(EnumType.STRING)
    private ChekTypeForSiteEntity type;
    private List<String> notFoundIndicators;
    private String elementSelector;
    private boolean enabled = true;
    private String notes;
}
