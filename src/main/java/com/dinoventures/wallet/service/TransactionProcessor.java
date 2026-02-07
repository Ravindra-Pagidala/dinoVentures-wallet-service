package com.dinoventures.wallet.service;

import com.dinoventures.wallet.entity.*;
import com.dinoventures.wallet.exception.ConflictException;
import com.dinoventures.wallet.repository.LedgerEntryRepository;
import com.dinoventures.wallet.repository.WalletRepository;
import com.dinoventures.wallet.repository.WalletTransactionRepository;
import com.dinoventures.wallet.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionProcessor {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public WalletTransaction processTransfer(
            String transactionType,
            Wallet fromWallet,
            Wallet toWallet,
            BigDecimal amount,
            String idempotencyKey,
            User user,
            AssetType assetType,
            LocalDateTime now) {

        log.debug("Processing transfer | type={} | from={} | to={} | amount={}",
                transactionType, fromWallet.getId(), toWallet.getId(), amount);

        // 🔒 CRITICAL FIX: Atomic debit with database-level validation
        // This single UPDATE statement prevents ALL race conditions
        int debitResult = walletRepository.atomicDebit(fromWallet.getId(), amount);

        if (debitResult == 0) {
            // Balance check failed at database level - reload to get actual balance
            Wallet reloadedWallet = walletRepository.findById(fromWallet.getId())
                    .orElseThrow(() -> new ConflictException("Wallet disappeared: " + fromWallet.getId()));

            BigDecimal currentBalance = NullSafeUtils.safeGetBigDecimal(reloadedWallet.getBalance());
            String reason = getFailureReason(transactionType);

            logFailedTransaction(transactionType, user, assetType, amount, reason, idempotencyKey);

            throw new ConflictException(reason + ": " + currentBalance);
        }

        // 🔒 Lock target wallet and credit atomically
        int creditResult = walletRepository.atomicCredit(toWallet.getId(), amount);

        if (creditResult == 0) {
            // Rollback the debit (very rare case - target wallet disappeared)
            walletRepository.atomicCredit(fromWallet.getId(), amount);
            throw new ConflictException("Target wallet disappeared: " + toWallet.getId());
        }

        // Reload both wallets to get updated balances
        Wallet updatedFrom = walletRepository.findById(fromWallet.getId())
                .orElseThrow(() -> new ConflictException("Source wallet reload failed"));
        Wallet updatedTo = walletRepository.findById(toWallet.getId())
                .orElseThrow(() -> new ConflictException("Target wallet reload failed"));

        // Create transaction record
        WalletTransaction tx = createSuccessTransaction(transactionType, user, assetType, amount, idempotencyKey, now);

        // Create double-entry ledger
        createDoubleEntryLedger(tx, updatedFrom, updatedTo, amount, now);

        log.debug("Transfer completed | tx={} | from_balance={} | to_balance={}",
                tx.getId(), updatedFrom.getBalance(), updatedTo.getBalance());

        return tx;
    }

    private String getFailureReason(String transactionType) {
        return switch(transactionType.toUpperCase()) {
            case "SPEND" -> "INSUFFICIENT_FUNDS";
            case "TOP_UP" -> "TREASURY_INSUFFICIENT";
            case "BONUS" -> "BONUS_POOL_EXHAUSTED";
            default -> "INSUFFICIENT_BALANCE";
        };
    }

    private void logFailedTransaction(String type, User user, AssetType asset, BigDecimal amount,
                                      String reason, String idempotencyKey) {
        try {
            WalletTransaction failedTx = WalletTransaction.builder()
                    .transactionType(type)
                    .user(user)
                    .assetType(asset)
                    .amount(amount)
                    .status("FAILED")
                    .failureReason(reason)
                    .idempotencyKey(idempotencyKey)
                    .createdAt(NullSafeUtils.safeNow())
                    .updatedAt(NullSafeUtils.safeNow())
                    .build();
            walletTransactionRepository.save(failedTx);
        } catch (Exception e) {
            log.error("Failed to log failed transaction: {}", e.getMessage());
        }
    }

    private WalletTransaction createSuccessTransaction(String transactionType, User user,
                                                       AssetType assetType, BigDecimal amount,
                                                       String idempotencyKey, LocalDateTime now) {
        WalletTransaction tx = WalletTransaction.builder()
                .transactionType(transactionType)
                .user(user)
                .assetType(assetType)
                .amount(amount)
                .status("SUCCESS")
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return walletTransactionRepository.save(tx);
    }

    private void createDoubleEntryLedger(WalletTransaction tx, Wallet fromWallet, Wallet toWallet,
                                         BigDecimal amount, LocalDateTime now) {
        createLedgerEntry(tx, fromWallet, "DEBIT", amount, now);
        createLedgerEntry(tx, toWallet, "CREDIT", amount, now);
    }

    private void createLedgerEntry(WalletTransaction tx, Wallet wallet, String entryType,
                                   BigDecimal amount, LocalDateTime now) {
        LedgerEntry entry = LedgerEntry.builder()
                .walletTransaction(tx)
                .wallet(wallet)
                .entryType(entryType)
                .amount(amount)
                .createdAt(now)
                .build();
        ledgerEntryRepository.save(entry);
    }
}