package com.dinoventures.wallet.controller;

import com.dinoventures.wallet.dto.ApiResponse;
import com.dinoventures.wallet.entity.AssetType;
import com.dinoventures.wallet.entity.User;
import com.dinoventures.wallet.repository.AssetTypeRepository;
import com.dinoventures.wallet.repository.UserRepository;
import com.dinoventures.wallet.repository.WalletRepository;
import com.dinoventures.wallet.utils.NullSafeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestController {

    private final UserRepository userRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final WalletRepository walletRepository;

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TestSetupResponse>> setupTestData() {
        log.info(" Test setup initiated");
        
        // Assets already created by initializer
        List<AssetType> assets = assetTypeRepository.findAll();
        
        // Create 5 test users
        List<User> testUsers = List.of("Player1", "Player2", "Player3", "Player4", "Player5")
                .stream()
                .map(name -> {
                    User user = User.builder()
                            .name(name)
                            .createdAt(NullSafeUtils.safeNow())
                            .build();
                    return userRepository.save(user);
                })
                .collect(Collectors.toList());

        log.info(" Created {} test users + {} assets", testUsers.size(), assets.size());
        
        TestSetupResponse response = new TestSetupResponse(
                testUsers.stream().map(u -> new TestUser(u.getId().toString(), u.getName())).collect(Collectors.toList()),
                assets.stream().map(a -> new TestAsset(a.getId().toString(), a.getCode())).collect(Collectors.toList())
        );
        
        return ResponseEntity.ok(ApiResponse.success("Test data ready", response));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<TestUser>>> listTestUsers() {
        List<User> users = userRepository.findAll();
        List<TestUser> testUsers = users.stream()
                .map(u -> new TestUser(u.getId().toString(), u.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", testUsers));
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<TestAsset>>> listAssets() {
        List<AssetType> assets = assetTypeRepository.findAll();
        List<TestAsset> testAssets = assets.stream()
                .map(a -> new TestAsset(a.getId().toString(), a.getCode()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Assets retrieved", testAssets));
    }
}

// DTOs
record TestUser(String id, String name) {}
record TestAsset(String id, String code) {}
record TestSetupResponse(List<TestUser> users, List<TestAsset> assets) {}
