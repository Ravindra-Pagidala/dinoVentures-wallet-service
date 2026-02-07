package com.dinoventures.wallet.dto;

import java.math.BigDecimal;

public record TopUpRequest(
        String userId,
        String assetCode,
        BigDecimal amount,
        String idempotencyKey
) {}
