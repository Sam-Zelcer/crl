package com.crl.hh.repository;

import com.crl.hh.repository.models.User;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findUserByUsername(String username);

    boolean existsUserByEmail(String email);
    boolean existsUserByUsername(String username);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.verified = :v WHERE u.username = :username")
    void updateVerifiedByUsername(@Param("username") String username, @Param("v") boolean v);

    void deleteUserByUsername(String username);
}
