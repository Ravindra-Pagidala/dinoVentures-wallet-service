package com.dinoventures.wallet.dto;

import java.util.List;

public record TransactionHistoryResponse(
        String userId,
        List<TransactionSummary> transactions
) {}
