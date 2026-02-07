package com.dinoventures.wallet.utils;

import com.dinoventures.wallet.entity.AssetType;
import com.dinoventures.wallet.entity.User;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public class NullSafeUtils {

    public String safeToString(Object value) {
        return value == null ? "null" : value.toString();
    }

    public UUID safeParseUUID(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public BigDecimal safeGetBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public BigDecimal safeToPositiveBigDecimal(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(BigDecimal.ZERO) <= 0) return null;
        return value;
    }

    public LocalDateTime safeNow() {
        return LocalDateTime.now();
    }

    public String safeUserId(User user) {
        return user == null || user.getId() == null ? "null" : user.getId().toString();
    }

    public String safeAssetCode(AssetType assetType) {
        return assetType == null ? "null" : safeToString(assetType.getCode());
    }

    public boolean isNullOrEmpty(String s) {
        return s == null || s.isBlank();
    }
}
