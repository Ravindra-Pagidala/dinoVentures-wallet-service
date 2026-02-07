package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
