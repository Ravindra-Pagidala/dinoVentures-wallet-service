package com.dinoventures.wallet.dto;

import java.math.BigDecimal;

public record SpendRequest(
        String userId,
        String assetCode,
        BigDecimal amount,
        String reference,
        String idempotencyKey
) {}
