# Dinoventures Wallet Service

A battle-hardened, closed-loop virtual currency system for games and loyalty platforms.

We built this to survive real-world abuse: thousands of concurrent top-ups, spends, and race-condition attacks while keeping balances correct, never going negative, and never creating or losing money.

## Core Problem We Solved

The hardest part of any wallet is **concurrency**.  
If two requests try to spend the same 10 gold at the exact same moment, you must never let both succeed.

### Approaches We Considered & Why We Rejected Most

| Approach                              | How it works                                      | Why we rejected it                                                                 |
|---------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------|
| Optimistic locking (version column)   | Increment version on every update                 | Too many retries under contention → bad user experience + DB load                 |
| Redis distributed lock                | Acquire lock before any DB operation              | Extra service, extra latency, extra failure mode                                   |
| SELECT FOR UPDATE (pessimistic)       | Lock both wallets before checking balance         | Worked, but longer lock time → lower throughput + occasional deadlocks            |
| **Atomic UPDATE + rows affected**     | `UPDATE wallets SET balance = balance - ? WHERE id = ? AND balance >= ?` | **Chosen** – shortest lock time, zero deadlock risk, database guarantees safety   |

### Why Atomic UPDATE Won

- The entire debit check + balance change happens in **one single SQL statement**.
- PostgreSQL executes it atomically → no race window.
- If 0 rows are affected → insufficient funds (we immediately reject).
- Lock duration is ~1–3 ms (only the UPDATE itself).
- No deadlock risk at all (no multi-statement locking).
- Works perfectly even when 20+ requests hit the same wallet simultaneously.

This is the pattern used by Stripe, Adyen, Roblox, and most serious gaming backends in production today.

## How Data Is Initialized (No Manual seed.sql Needed)

We do **not** use a seed.sql file.

Instead, everything is created automatically when the Spring Boot application starts:

- `WalletDataInitializer.java` (annotated with `@EventListener(ApplicationReadyEvent.class)`)
- Runs once after the app is fully up and the database connection is ready.
- Creates:
  - Assets → GOLD, DIAMONDS
  - 3 test users
  - System wallets (TREASURY = 1,000,000, BONUS = 50,000, REVENUE = 0) for each asset
- Idempotent (uses `.isEmpty()` checks) → safe to restart multiple times.

This means you never have to run any manual SQL to get the system working.

## Project Scripts – What They Do & How to Run Them

| Script               | What it does                                                                 | When to run it                          | Command |
|----------------------|------------------------------------------------------------------------------|-----------------------------------------|---------|
| `test-wallet.sh`     | Full production test suite (10 sections)<br>• Health + bootstrap<br>• Economy flow<br>• Idempotency<br>• Overdraft protection<br>• **Race condition stress tests** (20 spends + 30 top-ups)<br>• Multi-asset + audit | After `docker compose up` is running   | `./test-wallet.sh` |
| `validate-db.sh`     | Deep integrity audit (balances, double-entry, ledger vs wallet totals, orphans, race results, conservation of money) | After running `test-wallet.sh`         | `./validate-db.sh` |
| `docker-compose.yml` | Spins up PostgreSQL + the Spring Boot app (port 8080)                        | Every time you want to start the service | `docker compose up --build -d` |

### Recommended Workflow (Two Terminals)

**Terminal 1** – Start the service
```bash
docker compose up --build -d
# Wait 20–40 seconds until you see "Started WalletApplication"
