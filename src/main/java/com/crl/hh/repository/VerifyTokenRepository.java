package com.crl.hh.repository;

import com.crl.hh.repository.models.VerifyToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerifyTokenRepository extends JpaRepository<VerifyToken, Long> {

    Optional<VerifyToken> findVerifyTokenByToken(String token);

    void deleteVerifyTokenByToken(String token);
    void deleteByExpirationBefore(LocalDateTime time);
}
