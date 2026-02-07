package com.dinoventures.wallet.repository;

import com.dinoventures.wallet.entity.User;
import com.dinoventures.wallet.entity.Wallet;
import com.dinoventures.wallet.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerUserAndAssetTypeAndWalletType(User ownerUser, AssetType assetType, String walletType);

    List<Wallet> findAllByOwnerUser(User user);

    Optional<Wallet> findByOwnerUserIsNullAndAssetTypeAndWalletType(AssetType assetType, String walletType);


    // Atomic debit with balance validation in single UPDATE
    // This prevents race conditions by checking balance and updating in ONE database operation
    // Returns: number of rows updated (1 = success, 0 = insufficient balance or wallet not found)
    @Modifying
    @Query(value = """
        UPDATE wallets 
        SET balance = balance - :amount,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :walletId 
        AND balance >= :amount
        """, nativeQuery = true)
    int atomicDebit(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    // Atomic credit operation
    // Returns: number of rows updated (1 = success, 0 = wallet not found)
    @Modifying
    @Query(value = """
        UPDATE wallets 
        SET balance = balance + :amount,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :walletId
        """, nativeQuery = true)
    int atomicCredit(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

}
