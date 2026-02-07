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
import java.util.*;
import java.util.stream.Collectors;

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
            Wallet fromWallet,      // DEBIT wallet  
            Wallet toWallet,        // CREDIT wallet
            BigDecimal amount,
            String idempotencyKey,
            User user,
            AssetType assetType,
            LocalDateTime now) {

        log.debug("Processing transfer | type={} | from={} | to={} | amount={} | key={}",
                transactionType, fromWallet.getId(), toWallet.getId(), amount, idempotencyKey);

        // 1. Lock wallets (deadlock safe)
        Wallet[] lockedWallets = lockWalletsInOrder(fromWallet, toWallet);
        Wallet lockedFrom = lockedWallets[0];
        Wallet lockedTo = lockedWallets[1];

        // 2. Validate balance if needed
        validateBalanceForTransactionType(transactionType, lockedFrom, amount, user, assetType, idempotencyKey, now);

        // 3. Create and process transaction
        WalletTransaction tx = createPendingTransaction(transactionType, user, assetType, amount, idempotencyKey, now);
        createDoubleEntryLedger(tx, lockedFrom, lockedTo, amount, now);
        updateBalancesAndFinalize(tx, lockedFrom, lockedTo, amount, now);

        log.debug("Transfer completed | tx={} | fromBalance={} | toBalance={}",
                tx.getId(), lockedFrom.getBalance(), lockedTo.getBalance());

        return tx;
    }

    // === EXTRACTED METHODS ===

    private Wallet[] lockWalletsInOrder(Wallet fromWallet, Wallet toWallet) {
        List<UUID> walletIds = Arrays.asList(fromWallet.getId(), toWallet.getId());
        walletIds.sort(Comparator.comparing(UUID::toString));

        Map<UUID, Wallet> lockedWallets = walletIds.stream()
                .map(id -> walletRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new ConflictException("Wallet disappeared: " + id)))
                .collect(Collectors.toMap(Wallet::getId, w -> w));

        return new Wallet[]{
                lockedWallets.get(fromWallet.getId()),
                lockedWallets.get(toWallet.getId())
        };
    }

    private void validateBalanceForTransactionType(String transactionType, Wallet fromWallet,
                                                   BigDecimal amount, User user, AssetType assetType,
                                                   String idempotencyKey, LocalDateTime now) {
        if (!"SPEND".equalsIgnoreCase(transactionType)) {
            return; // No balance check for top-up/bonus
        }

        BigDecimal balance = NullSafeUtils.safeGetBigDecimal(fromWallet.getBalance());
        if (balance.compareTo(amount) < 0) {
            log.warn("Insufficient balance | wallet={} | balance={} | required={}",
                    fromWallet.getId(), balance, amount);

            createFailedTransaction("SPEND", user, assetType, amount,
                    "INSUFFICIENT_FUNDS", idempotencyKey, now);
            throw new ConflictException("Insufficient balance: " + balance);
        }
    }

    private WalletTransaction createPendingTransaction(String transactionType, User user,
                                                       AssetType assetType, BigDecimal amount,
                                                       String idempotencyKey, LocalDateTime now) {
        WalletTransaction tx = WalletTransaction.builder()
                .transactionType(transactionType)
                .user(user)
                .assetType(assetType)
                .amount(amount)
                .status("PENDING")
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

    private void updateBalancesAndFinalize(WalletTransaction tx, Wallet fromWallet, Wallet toWallet,
                                           BigDecimal amount, LocalDateTime now) {
        // Update balances
        BigDecimal fromBalance = NullSafeUtils.safeGetBigDecimal(fromWallet.getBalance()).subtract(amount);
        BigDecimal toBalance = NullSafeUtils.safeGetBigDecimal(toWallet.getBalance()).add(amount);

        fromWallet.setBalance(fromBalance);
        toWallet.setBalance(toBalance);
        fromWallet.setUpdatedAt(now);
        toWallet.setUpdatedAt(now);

        // Mark transaction SUCCESS
        tx.setStatus("SUCCESS");
        tx.setUpdatedAt(now);

        // Save everything
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
        walletTransactionRepository.save(tx);
    }

    private void createFailedTransaction(String transactionType, User user, AssetType assetType,
                                         BigDecimal amount, String failureReason,
                                         String idempotencyKey, LocalDateTime now) {
        WalletTransaction failedTx = WalletTransaction.builder()
                .transactionType(transactionType)
                .user(user)
                .assetType(assetType)
                .amount(amount)
                .status("FAILED")
                .failureReason(failureReason)
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        walletTransactionRepository.save(failedTx);
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