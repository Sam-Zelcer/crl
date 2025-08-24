package com.crl.hh.repository.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignUpRequest {

    private String username;
    private String email;
    private String password;
}
