package com.dinoventures.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserAssetBalance(
        String assetCode,
        BigDecimal balance
) {}

