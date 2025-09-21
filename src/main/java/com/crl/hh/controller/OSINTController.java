package com.crl.hh.controller;

import com.crl.hh.service.osint.OSINTService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/osint")
@RequiredArgsConstructor
public class OSINTController {

    private final OSINTService osintService;

    @GetMapping("/search-by-username")
    public List<String> searchByUsername(@RequestParam String username) {
        return osintService.searchByUsername(username);
    }
}
