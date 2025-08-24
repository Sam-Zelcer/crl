package com.crl.hh.controller.admin;

import com.crl.hh.repository.models.User;
import com.crl.hh.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/all-users")
    public List<User> getAllUsers() {
        return userService.allUsers();
    }
}
