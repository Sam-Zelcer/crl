package com.crl.hh.repository.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignInRequest {

    private String username;
    private String password;

}
