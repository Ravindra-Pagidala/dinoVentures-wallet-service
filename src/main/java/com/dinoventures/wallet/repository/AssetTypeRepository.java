package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetTypeRepository extends JpaRepository<AssetType, UUID> {
    Optional<AssetType> findByCode(String code);
}
