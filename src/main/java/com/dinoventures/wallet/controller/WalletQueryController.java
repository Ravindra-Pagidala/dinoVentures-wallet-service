package com.dinoventures.wallet.controller;

import com.dinoventures.wallet.dto.ApiResponse;
import com.dinoventures.wallet.dto.UserBalancesResponse;
import com.dinoventures.wallet.utils.NullSafeUtils;
import com.dinoventures.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletQueryController {

    private final WalletService walletService;

    @GetMapping("/{userId}/balances")
    public ResponseEntity<ApiResponse<UserBalancesResponse>> getUserBalances(@PathVariable String userId) {

        log.info("Get user balances API called | userId={}", NullSafeUtils.safeToString(userId));

        UserBalancesResponse balances = walletService.getUserBalances(userId);

        log.info("User balances fetched | userId={} | assets={}",
                NullSafeUtils.safeToString(balances.userId()),
                balances.balances() != null ? balances.balances().size() : 0);

        return ResponseEntity.ok(ApiResponse.success("User balances retrieved", balances));
    }

    // Optional simple health endpoint in this service namespace
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        log.info("Wallet service health check requested");
        return ResponseEntity.ok(ApiResponse.success("Wallet service is up", "OK"));
    }
}
