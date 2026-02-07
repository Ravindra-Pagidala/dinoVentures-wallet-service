package com.dinoventures.wallet.dto;

import java.math.BigDecimal;

public record BonusRequest(
        String userId,
        String assetCode,
        BigDecimal amount,
        String reason,
        String idempotencyKey
) {}
