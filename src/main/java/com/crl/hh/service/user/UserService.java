package com.crl.hh.service.user;

import com.crl.hh.repository.UserRepository;
import com.crl.hh.repository.VerifyTokenRepository;
import com.crl.hh.repository.models.dto.SignInRequest;
import com.crl.hh.repository.models.dto.SignUpRequest;
import com.crl.hh.repository.models.User;
import com.crl.hh.repository.models.VerifyToken;
import com.crl.hh.repository.models.enums.Role;
import com.crl.hh.service.jwt.JWTService;
import com.crl.hh.service.mail.MailService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final VerifyTokenRepository verifyTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;

    public String signUp(SignUpRequest signUpRequest) {
        if (userRepository.existsUserByUsername(signUpRequest.getUsername()) || userRepository.existsUserByEmail(signUpRequest.getEmail())) {
            return "User with email: " + signUpRequest.getEmail() + " or username: " + signUpRequest.getUsername() + " already exists";
        }

        if (
                signUpRequest.getPassword() == null || signUpRequest.getPassword().length() < 8 || signUpRequest.getPassword().length() > 200 || signUpRequest.getEmail() == null || signUpRequest.getEmail().length() > 150 || signUpRequest.getUsername() == null || signUpRequest.getUsername().length() > 120 || signUpRequest.getUsername().length() < 4
        ) {
            return "The user name must be at least 4 and no more than 120 characters,\n" +
                    "the user password must be at least 8 and no more than 200 characters";
        }

        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setRole(Role.USER);

        String token = passwordEncoder.encode(UUID.randomUUID().toString());
        System.out.println(token);

        VerifyToken verifyToken = new VerifyToken();
        verifyToken.setToken(token);
        verifyToken.setUsername(signUpRequest.getUsername());
        verifyToken.setExpiration(LocalDateTime.now().plusMinutes(20));

        try {
            userRepository.save(user);
            verifyTokenRepository.save(verifyToken);
        } catch (Exception e) {
            userRepository.deleteUserByUsername(user.getUsername());
            verifyTokenRepository.deleteVerifyTokenByToken(token);
            return "server err( " +  e.getMessage() + " )";
        }

        mailService.sendMail(user.getEmail(), "verify", "http://localhost:8080/unauthorized/verify?token=" + token);

        return "Success, now you have to pass verify thorough email, you have only 20 minutes";
    }

    @Transactional
    public String verify(String token) {
        Optional<VerifyToken> verifyToken = verifyTokenRepository.findVerifyTokenByToken(token);

        if (verifyToken.isEmpty() || verifyToken.get().getExpiration().isBefore(LocalDateTime.now())) {
            return "Invalid token";
        }

        try {
            userRepository.updateVerifiedByUsername(verifyToken.get().getUsername(), true);
            verifyTokenRepository.deleteVerifyTokenByToken(token);
        } catch (Exception e) {
            userRepository.deleteUserByUsername(verifyToken.get().getUsername());
            verifyTokenRepository.deleteVerifyTokenByToken(token);
            return "server err( " +  e.getMessage() + " )";
        }
        return "User was created";
    }

    public String signIn(SignInRequest signInRequest) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(signInRequest.getUsername(), signInRequest.getPassword()));
        if (!authentication.isAuthenticated()) {
            return "bad credentials";
        }
        return jwtService.generateToken(signInRequest.getUsername());
    }

    public List<User> allUsers() {
        return userRepository.findAll();
    }


    @Transactional
    @Scheduled(fixedRateString = "${verify.cleanup.rate.ms:1200000}")
    public void cleanExpiredTokens() {
        verifyTokenRepository.deleteByExpirationBefore(LocalDateTime.now().plusMinutes(20));
    }
}
