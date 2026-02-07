package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.entity.User;
import com.dinoventures.wallet.entity.Wallet;
import com.dinoventures.wallet.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerUserAndAssetTypeAndWalletType(User ownerUser, AssetType assetType, String walletType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    List<Wallet> findAllByOwnerUser(User user);

    Optional<Wallet> findByOwnerUserIsNullAndAssetTypeAndWalletType(AssetType assetType, String walletType);

}
