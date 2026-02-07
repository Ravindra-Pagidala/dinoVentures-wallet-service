package com.dinoventures.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "wallet_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_tx_idempotency", columnNames = {"idempotency_key"})
    }
)
public class WalletTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String transactionType; // TOP_UP, BONUS, SPEND

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @Column(nullable = false)
    private BigDecimal amount; // positive

    @Column(nullable = false)
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
