# Dinoventures Wallet Service  
**Closed-loop virtual currency engine for games & loyalty platforms**

A production-grade wallet backend built to handle **Gold Coins**, **Diamonds**, and other in-app currencies — safely, even when thousands of requests hammer it at once.

Never loses a coin.  
Never creates money from thin air.  
Never allows negative balances.  
Always tells the full story via double-entry ledger.

---

## 🎯 What This Project Does

This is a **closed-loop wallet system** — virtual credits live only inside your application.

- Users **earn** credits via top-up (paid) or bonus (free)  
- Users **spend** credits on in-game items/services  
- Credits **cannot** be sent to other users  
- Credits **cannot** be withdrawn or converted to real money/crypto  

Perfect for mobile/PC games (like V-Bucks or Robux) or loyalty/rewards platforms.

Core promise: **correct balances + complete audit trail — no matter how many concurrent requests arrive.**

---

## ✅ Requirements Fulfillment Checklist

| Requirement                              | Done? | How / Where                                                                 |
|------------------------------------------|-------|-----------------------------------------------------------------------------|
| Data seeding (assets, system wallets, users) | ✅    | Automatic on app startup via `WalletDataInitializer` (no manual seed.sql)   |
| REST API endpoints                       | ✅    | `/api/v1/wallets/topup`, `/bonus`, `/spend`, `/balances/{userId}`           |
| Transactional top-up / bonus / spend     | ✅    | `@Transactional` + atomic DB operations                                     |
| Concurrency & race condition protection  | ✅    | Atomic `UPDATE … WHERE balance >= ?` pattern                                |
| Idempotency (safe retries)               | ✅    | Unique DB constraint + check inside transaction                             |
| Double-entry ledger                      | ✅    | `ledger_entries` table — every success tx has DEBIT + CREDIT                |
| Docker + docker-compose                  | ✅    | `Dockerfile` + `docker-compose.yml`                                         |
| Clear README (tech choices + concurrency) | ✅    | This file                                                                  |

---

## 🛠 Technology & Concurrency Decisions

| Component          | Choice              | Why (2025 view)                                                                     |
|---------------------|---------------------|-------------------------------------------------------------------------------------|
| Framework           | **Spring Boot**     | Mature transactions, excellent JPA, huge ecosystem, easy team onboarding           |
| Database            | **PostgreSQL**      | Best MVCC + ACID for high-contention financial workloads                            |
| Concurrency control | **Atomic UPDATE**   | Shortest lock, **zero deadlock risk**, DB guarantees atomicity — no race window    |

**Rejected alternatives**  
- Optimistic locking → too many retries under load  
- SELECT FOR UPDATE → longer locks, occasional deadlocks  
- Redis lock → extra service, latency, complexity  

**How atomic UPDATE works** (simplified):

```sql
UPDATE wallets 
SET balance = balance - :amount 
WHERE id = :walletId AND balance >= :amount;
```

- 0 rows affected → insufficient funds (409)  
- Lock lasts microseconds  
- No multi-statement transaction needed → **no deadlocks**

This pattern is battle-tested in gaming wallets and fintech ledgers.

---

## 🚀 How to Run (Step-by-Step)

### 1. Clone the repository

```bash
git clone https://github.com/Ravindra-Pagidala/wallet-service.git

cd wallet-service
```

### 2. Start the service (Terminal 1)

```bash
# Build the Docker image & start PostgreSQL + Spring Boot
docker compose up --build -d

# Wait 20–40 seconds until ready
# Optional: watch logs in real-time
docker compose logs -f wallet-app
```

**Automatic initialization happens here** — when the app starts:

- Assets created: GOLD, DIAMONDS  
- 3 test users created  
- System wallets created: TREASURY (1,000,000), BONUS (50,000), REVENUE (0) per asset  

No manual SQL or seed script needed.

### 3. Run the tests (Terminal 2 – open a new terminal)

```bash
# Make scripts executable (only needed the first time)
chmod +x test-wallet.sh
chmod +x validate-db.sh

# Run the full production test suite
./test-wallet.sh
```

Expected final line:

```
🎉 ✅ PRODUCTION READY! 10/10 ALL TESTS PASS
✅ ACID | Race-Proof | Double-Entry
```

Then (optional) deep database validation:

```bash
./validate-db.sh
```

### 4. Reset everything for a clean test

```bash
docker compose down -v
# Then restart with:
docker compose up --build -d
```

---

## 📋 Scripts – Quick Reference

| Script              | What it tests / does                                                        | Command                     |
|---------------------|-----------------------------------------------------------------------------|-----------------------------|
| `test-wallet.sh`    | Full suite: economy flow, idempotency, overdraft, **race conditions**, audit | `./test-wallet.sh`         |
| `validate-db.sh`    | Deep integrity: balances, ledger matching, money conservation, race proof   | `./validate-db.sh`         |
| `docker-compose.yml`| Launches PostgreSQL + Spring Boot app (exposed on http://localhost:8080)    | `docker compose up --build`|

---

## 🎯 Final Summary

This wallet is hardened against real abuse:

- 20 concurrent 10-gold spends from 150 → max ~15 succeed  
- 30 concurrent 5-gold top-ups from treasury 100 → max ~20 succeed  
- No negatives ever  
- Full double-entry audit trail  
- Safe retries via idempotency keys  
- Everything auto-initializes on startup  
- Everything runs in Docker

Ready for high-traffic gaming or loyalty backends.

Built in Hyderabad with persistence and coffee.  
— Ravindra Pagidala
