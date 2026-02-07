package com.dinoventures.wallet.service;

import com.dinoventures.wallet.dto.*;
import com.dinoventures.wallet.entity.*;
import com.dinoventures.wallet.exception.ConflictException;
import com.dinoventures.wallet.exception.ResourceNotFoundException;
import com.dinoventures.wallet.exception.ValidationException;
import com.dinoventures.wallet.repository.*;
import com.dinoventures.wallet.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionProcessor transactionProcessor;
    private final EntityManager entityManager;  // 🔧 ADD THIS

    @Transactional
    public TopUpResponse topUp(TopUpRequest request) {
        log.info("Top-up | user={} asset={} amt={} key={}",
                request.userId(), request.assetCode(), request.amount(), request.idempotencyKey());

        validateTopUpRequest(request);
        checkIdempotency(request.idempotencyKey(), "TOP_UP");

        User user = findUserOrThrow(request.userId());
        AssetType asset = findAssetOrThrow(request.assetCode());

        Wallet userWallet = getOrCreateUserWallet(user, asset);
        Wallet treasuryWallet = getSystemWalletOrThrow(asset, "TREASURY");

        LocalDateTime now = NullSafeUtils.safeNow();
        WalletTransaction tx = transactionProcessor.processTransfer(
                "TOP_UP", treasuryWallet, userWallet, request.amount(),
                request.idempotencyKey(), user, asset, now);

        //  refresh from database to get updated balance
        entityManager.refresh(userWallet);

        log.info("Top-up success | tx={} user={} balance={}", tx.getId(), user.getId(), userWallet.getBalance());

        return mapToTopUpResponse(tx, userWallet);
    }

    @Transactional
    public BonusResponse bonus(BonusRequest request) {
        log.info("Bonus | user={} asset={} amt={} reason={} key={}",
                request.userId(), request.assetCode(), request.amount(),
                request.reason(), request.idempotencyKey());

        validateBonusRequest(request);
        checkIdempotency(request.idempotencyKey(), "BONUS");

        User user = findUserOrThrow(request.userId());
        AssetType asset = findAssetOrThrow(request.assetCode());

        Wallet userWallet = getOrCreateUserWallet(user, asset);
        Wallet bonusWallet = getSystemWalletOrThrow(asset, "BONUS");

        LocalDateTime now = NullSafeUtils.safeNow();
        WalletTransaction tx = transactionProcessor.processTransfer(
                "BONUS", bonusWallet, userWallet, request.amount(),
                request.idempotencyKey(), user, asset, now);

        //  refresh from database to get updated balance
        entityManager.refresh(userWallet);

        log.info("Bonus success | tx={} user={} balance={}", tx.getId(), user.getId(), userWallet.getBalance());

        return mapToBonusResponse(tx, userWallet);
    }

    @Transactional
    public SpendResponse spend(SpendRequest request) {
        log.info("Spend | user={} asset={} amt={} ref={} key={}",
                request.userId(), request.assetCode(), request.amount(),
                request.reference(), request.idempotencyKey());

        validateSpendRequest(request);
        checkIdempotency(request.idempotencyKey(), "SPEND");

        User user = findUserOrThrow(request.userId());
        AssetType asset = findAssetOrThrow(request.assetCode());

        Wallet userWallet = getOrCreateUserWallet(user, asset);
        Wallet revenueWallet = getSystemWalletOrThrow(asset, "REVENUE");

        LocalDateTime now = NullSafeUtils.safeNow();
        WalletTransaction tx = transactionProcessor.processTransfer(
                "SPEND", userWallet, revenueWallet, request.amount(),
                request.idempotencyKey(), user, asset, now);

        // refresh from database to get updated balance
        entityManager.refresh(userWallet);

        log.info("Spend success | tx={} user={} balance={}", tx.getId(), user.getId(), userWallet.getBalance());

        return mapToSpendResponse(tx, userWallet);
    }

    public UserBalancesResponse getUserBalances(String userIdStr) {
        log.info("Get balances | user={}", userIdStr);
        User user = findUserOrThrow(userIdStr);

        List<Wallet> wallets = walletRepository.findAllByOwnerUser(user);
        List<UserAssetBalance> balances = wallets.stream()
                .map(w -> new UserAssetBalance(
                        w.getAssetType().getCode(),
                        NullSafeUtils.safeGetBigDecimal(w.getBalance())
                ))
                .collect(Collectors.toList());

        log.info("Balances fetched | user={} | count={}", user.getId(), balances.size());
        return new UserBalancesResponse(user.getId().toString(), balances);
    }

    // === SHARED VALIDATION ===

    private void validateTopUpRequest(TopUpRequest request) {
        validateCommonRequest(request.userId(), request.assetCode(), request.amount(), request.idempotencyKey());
    }

    private void validateBonusRequest(BonusRequest request) {
        validateCommonRequest(request.userId(), request.assetCode(), request.amount(), request.idempotencyKey());
    }

    private void validateSpendRequest(SpendRequest request) {
        validateCommonRequest(request.userId(), request.assetCode(), request.amount(), request.idempotencyKey());
    }

    private void validateCommonRequest(String userId, String assetCode, BigDecimal amount, String idempotencyKey) {
        if (NullSafeUtils.isNullOrEmpty(userId)) throw new ValidationException("User ID required");
        if (NullSafeUtils.isNullOrEmpty(assetCode)) throw new ValidationException("Asset code required");
        if (NullSafeUtils.safeToPositiveBigDecimal(amount) == null) throw new ValidationException("Amount must be positive");
        if (NullSafeUtils.isNullOrEmpty(idempotencyKey)) throw new ValidationException("Idempotency key required");
    }

    // === SHARED HELPERS ===

    private void checkIdempotency(String key, String type) {
        WalletTransaction existing = findTransactionByIdempotencyKey(key);
        if (existing != null) {
            if ("SUCCESS".equalsIgnoreCase(existing.getStatus())) {
                log.info("Idempotent {} detected | key={} | tx={}", type, key, existing.getId());
                throw new ConflictException("Request already processed: " + key);
            }
            if ("FAILED".equalsIgnoreCase(existing.getStatus())) {
                log.warn("Previous {} failed | key={}", type, key);
                throw new ConflictException("Previous request failed: " + key);
            }
        }
    }

    private User findUserOrThrow(String userIdStr) {
        UUID userId = NullSafeUtils.safeParseUUID(userIdStr);
        if (userId == null) throw new ValidationException("Invalid userId format");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userIdStr));
    }

    private AssetType findAssetOrThrow(String assetCode) {
        return assetTypeRepository.findByCode(assetCode)
                .orElseThrow(() -> new ValidationException("Unknown asset: " + assetCode));
    }

    private WalletTransaction findTransactionByIdempotencyKey(String key) {
        if (NullSafeUtils.isNullOrEmpty(key)) return null;
        return walletTransactionRepository.findByIdempotencyKey(key).orElse(null);
    }

    private Wallet getOrCreateUserWallet(User user, AssetType assetType) {
        return walletRepository.findByOwnerUserAndAssetTypeAndWalletType(user, assetType, "USER")
                .orElseGet(() -> {
                    Wallet wallet = Wallet.builder()
                            .ownerUser(user)
                            .assetType(assetType)
                            .walletType("USER")
                            .balance(BigDecimal.ZERO)
                            .createdAt(NullSafeUtils.safeNow())
                            .updatedAt(NullSafeUtils.safeNow())
                            .build();
                    return walletRepository.save(wallet);
                });
    }

    private Wallet getSystemWalletOrThrow(AssetType assetType, String walletType) {
        return walletRepository.findByOwnerUserIsNullAndAssetTypeAndWalletType(assetType, walletType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "System wallet missing: asset=" + assetType.getCode() + " type=" + walletType));
    }

    // === MAPPING ===

    private TopUpResponse mapToTopUpResponse(WalletTransaction tx, Wallet userWallet) {
        return new TopUpResponse(
                tx.getId(), tx.getUser().getId().toString(), tx.getAssetType().getCode(),
                tx.getAmount(), tx.getStatus(), NullSafeUtils.safeGetBigDecimal(userWallet.getBalance())
        );
    }

    private BonusResponse mapToBonusResponse(WalletTransaction tx, Wallet userWallet) {
        return new BonusResponse(
                tx.getId(), tx.getUser().getId().toString(), tx.getAssetType().getCode(),
                tx.getAmount(), tx.getStatus(), NullSafeUtils.safeGetBigDecimal(userWallet.getBalance())
        );
    }

    private SpendResponse mapToSpendResponse(WalletTransaction tx, Wallet userWallet) {
        return new SpendResponse(
                tx.getId(), tx.getUser().getId().toString(), tx.getAssetType().getCode(),
                tx.getAmount(), tx.getStatus(), NullSafeUtils.safeGetBigDecimal(userWallet.getBalance())
        );
    }
}