package com.dinoventures.wallet.dto;

import java.util.List;

public record UserBalancesResponse(
        String userId,
        List<UserAssetBalance> balances
) {}