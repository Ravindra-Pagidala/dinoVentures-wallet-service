package com.dinoventures.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionSummary(
        UUID transactionId,
        String transactionType,   // TOP_UP, BONUS, SPEND
        String assetCode,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt
) {}
