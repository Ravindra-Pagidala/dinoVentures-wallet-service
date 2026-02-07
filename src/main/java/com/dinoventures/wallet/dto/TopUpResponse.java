package com.dinoventures.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopUpResponse(
        UUID transactionId,
        String userId,
        String assetCode,
        BigDecimal amount,
        String status,
        BigDecimal newBalance
) {}
