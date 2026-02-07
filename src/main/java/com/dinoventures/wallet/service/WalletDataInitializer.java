package com.dinoventures.wallet.service;

import com.dinoventures.wallet.entity.AssetType;
import com.dinoventures.wallet.entity.User;
import com.dinoventures.wallet.entity.Wallet;
import com.dinoventures.wallet.repository.AssetTypeRepository;
import com.dinoventures.wallet.repository.UserRepository;
import com.dinoventures.wallet.repository.WalletRepository;
import com.dinoventures.wallet.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletDataInitializer {

    private final AssetTypeRepository assetTypeRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info(" Initializing wallet data...");

        // 1. Assets (safe)
        createAssetIfMissing("GOLD", "Gold Coins");
        createAssetIfMissing("DIAMONDS", "Diamonds");
        createAssetIfMissing("BONUS_POINTS", "Bonus Points");

        // 2. Users (FIXED: Let Hibernate auto-generate IDs)
        createUserIfMissing("Test User 1");
        createUserIfMissing("Test User 2");
        createUserIfMissing("Test User 3");

        // 3. System Wallets
        createSystemWallets("GOLD");
        createSystemWallets("DIAMONDS");
        createSystemWallets("BONUS_POINTS");

        log.info(" Data initialization complete!");
    }

    private void createAssetIfMissing(String code, String name) {
        if (assetTypeRepository.findByCode(code).isEmpty()) {
            AssetType asset = AssetType.builder()
                    .code(code)
                    .displayName(name)
                    .createdAt(NullSafeUtils.safeNow())
                    .build();
            assetTypeRepository.save(asset);
            log.info("Created asset: {}", code);
        }
    }

    private void createUserIfMissing(String name) {
        if (userRepository.count() < 3) {  // Only create if we need more users
            User user = User.builder()
                    .name(name)
                    .createdAt(NullSafeUtils.safeNow())
                    .build();
            try {
                userRepository.save(user);
                log.info("Created user: {}", name);
            } catch (Exception e) {
                log.warn("User {} already exists or conflict, skipping", name);
            }
        }
    }

    private void createSystemWallets(String assetCode) {
        AssetType asset = assetTypeRepository.findByCode(assetCode)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetCode));

        createSystemWallet(asset, "TREASURY", BigDecimal.valueOf(1000000));
        createSystemWallet(asset, "BONUS", BigDecimal.valueOf(50000));
        createSystemWallet(asset, "REVENUE", BigDecimal.ZERO);
    }

    private void createSystemWallet(AssetType asset, String type, BigDecimal balance) {
        if (walletRepository.findByOwnerUserIsNullAndAssetTypeAndWalletType(asset, type).isEmpty()) {
            Wallet wallet = Wallet.builder()
                    .assetType(asset)
                    .walletType(type)
                    .balance(balance)
                    .createdAt(NullSafeUtils.safeNow())
                    .updatedAt(NullSafeUtils.safeNow())
                    .build();
            walletRepository.save(wallet);
            log.info("Created {} wallet for {}: {}", type, asset.getCode(), balance);
        }
    }
}
