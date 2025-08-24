package com.crl.hh.controller.unauthorized;

import com.crl.hh.repository.models.dto.SignInRequest;
import com.crl.hh.repository.models.dto.SignUpRequest;
import com.crl.hh.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/unauthorized")
@RequiredArgsConstructor
public class UnauthorizedController {

    private final UserService userService;

    @PostMapping("/sign-up")
    public String signUp(@RequestBody SignUpRequest signUpRequest) {
        return userService.signUp(signUpRequest);
    }

    @GetMapping("/verify")
    public String verify(@RequestParam String token) {
        return userService.verify(token);
    }

    @PostMapping("/sign-in")
    public String signIn(@RequestBody SignInRequest signInRequest) {
        return userService.signIn(signInRequest);
    }
}
