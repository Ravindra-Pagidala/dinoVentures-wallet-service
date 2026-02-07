package com.dinoventures.wallet.controller;

import com.dinoventures.wallet.dto.*;
import com.dinoventures.wallet.service.WalletService;
import com.dinoventures.wallet.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletOperationsController {

    private final WalletService walletService;

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<TopUpResponse>> topUp(@RequestBody TopUpRequest request) {

        log.info("Top-up API called | userId={} | assetCode={} | amount={} | key={}",
                NullSafeUtils.safeToString(request.userId()),
                NullSafeUtils.safeToString(request.assetCode()),
                NullSafeUtils.safeToString(request.amount()),
                NullSafeUtils.safeToString(request.idempotencyKey()));

        TopUpResponse response = walletService.topUp(request);

        log.info("Top-up completed | txId={} | userId={} | assetCode={} | amount={} | newBalance={}",
                NullSafeUtils.safeToString(response.transactionId()),
                NullSafeUtils.safeToString(response.userId()),
                NullSafeUtils.safeToString(response.assetCode()),
                NullSafeUtils.safeToString(response.amount()),
                NullSafeUtils.safeToString(response.newBalance()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Top-up successful", response));
    }

    @PostMapping("/bonus")
    public ResponseEntity<ApiResponse<BonusResponse>> grantBonus(@RequestBody BonusRequest request) {

        log.info("Bonus API called | userId={} | assetCode={} | amount={} | reason={} | key={}",
                NullSafeUtils.safeToString(request.userId()),
                NullSafeUtils.safeToString(request.assetCode()),
                NullSafeUtils.safeToString(request.amount()),
                NullSafeUtils.safeToString(request.reason()),
                NullSafeUtils.safeToString(request.idempotencyKey()));

        BonusResponse response = walletService.bonus(request);

        log.info("Bonus granted | txId={} | userId={} | assetCode={} | amount={} | newBalance={}",
                NullSafeUtils.safeToString(response.transactionId()),
                NullSafeUtils.safeToString(response.userId()),
                NullSafeUtils.safeToString(response.assetCode()),
                NullSafeUtils.safeToString(response.amount()),
                NullSafeUtils.safeToString(response.newBalance()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bonus granted successfully", response));
    }

    @PostMapping("/spend")
    public ResponseEntity<ApiResponse<SpendResponse>> spend(@RequestBody SpendRequest request) {

        log.info("Spend API called | userId={} | assetCode={} | amount={} | reference={} | key={}",
                NullSafeUtils.safeToString(request.userId()),
                NullSafeUtils.safeToString(request.assetCode()),
                NullSafeUtils.safeToString(request.amount()),
                NullSafeUtils.safeToString(request.reference()),
                NullSafeUtils.safeToString(request.idempotencyKey()));

        SpendResponse response = walletService.spend(request);

        log.info("Spend completed | txId={} | userId={} | assetCode={} | amount={} | newBalance={}",
                NullSafeUtils.safeToString(response.transactionId()),
                NullSafeUtils.safeToString(response.userId()),
                NullSafeUtils.safeToString(response.assetCode()),
                NullSafeUtils.safeToString(response.amount()),
                NullSafeUtils.safeToString(response.newBalance()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Spend successful", response));
    }
}
