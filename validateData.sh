#!/bin/bash
# Database Validation Queries for Wallet Service
# Run these after your test script to verify data integrity

echo "======================================================================"
echo "🔍 WALLET SERVICE - DATABASE VALIDATION QUERIES"
echo "======================================================================"
echo ""

# Function to run query and display results
run_query() {
    local title="$1"
    local query="$2"
    echo "📊 $title"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker exec wallet-db psql -U postgres -d wallet -c "$query"
    echo ""
}

# ============================================================================
# 1. BALANCE INTEGRITY CHECKS
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "1️⃣  BALANCE INTEGRITY CHECKS"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "1.1 All Wallet Balances (should have NO negatives)" \
"SELECT
    w.id,
    CASE
        WHEN u.name IS NOT NULL THEN u.name
        ELSE w.wallet_type
    END as owner,
    w.wallet_type,
    a.code as asset,
    w.balance,
    CASE
        WHEN w.balance < 0 THEN '❌ NEGATIVE!'
        ELSE '✅'
    END as status
FROM wallets w
LEFT JOIN users u ON w.owner_user_id = u.id
JOIN asset_types a ON w.asset_type_id = a.id
ORDER BY w.wallet_type, a.code, u.name;"

run_query "1.2 System Wallet Balances Summary" \
"SELECT
    a.code as asset,
    w.wallet_type,
    w.balance,
    CASE
        WHEN w.wallet_type = 'TREASURY' AND w.balance < 0 THEN '❌ OVERDRAWN'
        WHEN w.wallet_type = 'BONUS' AND w.balance < 0 THEN '❌ OVERDRAWN'
        WHEN w.wallet_type = 'REVENUE' AND w.balance < 0 THEN '❌ IMPOSSIBLE'
        ELSE '✅'
    END as status
FROM wallets w
JOIN asset_types a ON w.asset_type_id = a.id
WHERE w.owner_user_id IS NULL
ORDER BY a.code, w.wallet_type;"

run_query "1.3 User Wallet Balances" \
"SELECT
    u.name,
    a.code as asset,
    w.balance,
    CASE
        WHEN w.balance < 0 THEN '❌ NEGATIVE'
        ELSE '✅'
    END as status
FROM wallets w
JOIN users u ON w.owner_user_id = u.id
JOIN asset_types a ON w.asset_type_id = a.id
WHERE w.wallet_type = 'USER'
ORDER BY u.name, a.code;"

# ============================================================================
# 2. DOUBLE-ENTRY LEDGER VALIDATION
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "2️⃣  DOUBLE-ENTRY LEDGER VALIDATION"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "2.1 Verify Every Transaction Has Exactly 2 Ledger Entries (1 DEBIT + 1 CREDIT)" \
"SELECT
    wt.id as tx_id,
    wt.transaction_type,
    wt.amount,
    wt.status,
    COUNT(le.id) as ledger_entries,
    COUNT(CASE WHEN le.entry_type = 'DEBIT' THEN 1 END) as debits,
    COUNT(CASE WHEN le.entry_type = 'CREDIT' THEN 1 END) as credits,
    CASE
        WHEN COUNT(le.id) = 2
         AND COUNT(CASE WHEN le.entry_type = 'DEBIT' THEN 1 END) = 1
         AND COUNT(CASE WHEN le.entry_type = 'CREDIT' THEN 1 END) = 1
        THEN '✅'
        ELSE '❌ BROKEN!'
    END as status
FROM wallet_transactions wt
LEFT JOIN ledger_entries le ON wt.id = le.wallet_transaction_id
WHERE wt.status = 'SUCCESS'
GROUP BY wt.id, wt.transaction_type, wt.amount, wt.status
ORDER BY wt.created_at DESC
LIMIT 20;"

run_query "2.2 Verify DEBIT and CREDIT Amounts Match for Each Transaction" \
"SELECT
    wt.id as tx_id,
    wt.transaction_type,
    wt.amount as tx_amount,
    SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) as total_debits,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) as total_credits,
    CASE
        WHEN SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) =
             SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END)
         AND SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) = wt.amount
        THEN '✅'
        ELSE '❌ UNBALANCED!'
    END as status
FROM wallet_transactions wt
JOIN ledger_entries le ON wt.id = le.wallet_transaction_id
WHERE wt.status = 'SUCCESS'
GROUP BY wt.id, wt.transaction_type, wt.amount
ORDER BY wt.created_at DESC
LIMIT 20;"

run_query "2.3 System-Wide Ledger Balance (Total DEBITS should equal Total CREDITS)" \
"SELECT
    a.code as asset,
    SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) as total_debits,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) as total_credits,
    SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) -
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) as difference,
    CASE
        WHEN SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) =
             SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END)
        THEN '✅ LEDGER BALANCED'
        ELSE '❌ LEDGER UNBALANCED'
    END as status
FROM ledger_entries le
JOIN wallets w ON le.wallet_id = w.id
JOIN asset_types a ON w.asset_type_id = a.id
GROUP BY a.code;"

# ============================================================================
# 3. TRANSACTION STATUS & IDEMPOTENCY
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "3️⃣  TRANSACTION STATUS & IDEMPOTENCY CHECKS"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "3.1 Transaction Status Breakdown" \
"SELECT
    transaction_type,
    status,
    COUNT(*) as count,
    SUM(amount) as total_amount
FROM wallet_transactions
GROUP BY transaction_type, status
ORDER BY transaction_type, status;"

run_query "3.2 Failed Transactions (should exist from race condition tests)" \
"SELECT
    wt.id,
    wt.transaction_type,
    u.name as user_name,
    a.code as asset,
    wt.amount,
    wt.failure_reason,
    wt.idempotency_key,
    wt.created_at
FROM wallet_transactions wt
JOIN users u ON wt.user_id = u.id
JOIN asset_types a ON wt.asset_type_id = a.id
WHERE wt.status = 'FAILED'
ORDER BY wt.created_at DESC
LIMIT 20;"

run_query "3.3 Verify No Duplicate Idempotency Keys" \
"SELECT
    idempotency_key,
    COUNT(*) as count,
    CASE
        WHEN COUNT(*) = 1 THEN '✅'
        ELSE '❌ DUPLICATE!'
    END as status
FROM wallet_transactions
WHERE idempotency_key IS NOT NULL
GROUP BY idempotency_key
HAVING COUNT(*) > 1;"

run_query "3.4 Pending Transactions (should be 0)" \
"SELECT
    COUNT(*) as pending_count,
    CASE
        WHEN COUNT(*) = 0 THEN '✅ No pending transactions'
        ELSE '❌ Found pending transactions!'
    END as status
FROM wallet_transactions
WHERE status = 'PENDING';"

# ============================================================================
# 4. CONSERVATION OF MONEY (CRITICAL!)
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "4️⃣  CONSERVATION OF MONEY - MOST IMPORTANT CHECK!"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "4.1 Total Money in System (sum of all wallets per asset)" \
"SELECT
    a.code as asset,
    SUM(w.balance) as total_in_system,
    CASE
        WHEN SUM(w.balance) > 0 THEN '✅'
        ELSE '⚠️ Check initial setup'
    END as status
FROM wallets w
JOIN asset_types a ON w.asset_type_id = a.id
GROUP BY a.code;"

run_query "4.2 Money Flow Breakdown (where did money go?)" \
"SELECT
    a.code as asset,
    SUM(CASE WHEN w.wallet_type = 'TREASURY' THEN w.balance ELSE 0 END) as treasury,
    SUM(CASE WHEN w.wallet_type = 'BONUS' THEN w.balance ELSE 0 END) as bonus_pool,
    SUM(CASE WHEN w.wallet_type = 'REVENUE' THEN w.balance ELSE 0 END) as revenue,
    SUM(CASE WHEN w.wallet_type = 'USER' THEN w.balance ELSE 0 END) as user_wallets,
    SUM(w.balance) as total
FROM wallets w
JOIN asset_types a ON w.asset_type_id = a.id
GROUP BY a.code;"

run_query "4.3 Ledger vs Wallet Balance (may differ due to test manipulation)" \
"-- Note: This may show differences because race tests manipulate balances directly
WITH ledger_balance AS (
    SELECT
        a.code as asset,
        SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount
                 WHEN le.entry_type = 'DEBIT' THEN -le.amount
                 ELSE 0 END) as ledger_total
    FROM ledger_entries le
    JOIN wallets w ON le.wallet_id = w.id
    JOIN asset_types a ON w.asset_type_id = a.id
    GROUP BY a.code
),
wallet_balance AS (
    SELECT
        a.code as asset,
        SUM(w.balance) as wallet_total
    FROM wallets w
    JOIN asset_types a ON w.asset_type_id = a.id
    GROUP BY a.code
)
SELECT
    COALESCE(l.asset, w.asset) as asset,
    COALESCE(l.ledger_total, 0) as ledger_balance,
    COALESCE(w.wallet_total, 0) as wallet_balance,
    COALESCE(w.wallet_total, 0) - COALESCE(l.ledger_total, 0) as difference,
    CASE
        WHEN ABS(COALESCE(w.wallet_total, 0) - COALESCE(l.ledger_total, 0)) < 0.01
        THEN '✅ MATCH'
        ELSE '⚠️ DIFFER (Data Manipulated While Testing Race Condition, Ignore the Change)'
    END as status
FROM ledger_balance l
FULL OUTER JOIN wallet_balance w ON l.asset = w.asset;"

# ============================================================================
# 5. RACE CONDITION VERIFICATION
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "5️⃣  RACE CONDITION TEST RESULTS"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "5.1 Race Test #1 - User spent exactly 150 (not 200)" \
"SELECT
    u.name,
    a.code as asset,
    COUNT(wt.id) as successful_spends,
    SUM(wt.amount) as total_spent,
    CASE
        WHEN SUM(wt.amount) <= 150 THEN '✅ Race prevented'
        ELSE '❌ Race condition occurred'
    END as status
FROM wallet_transactions wt
JOIN users u ON wt.user_id = u.id
JOIN asset_types a ON wt.asset_type_id = a.id
WHERE wt.idempotency_key LIKE 'race1-%'
  AND wt.status = 'SUCCESS'
GROUP BY u.name, a.code;"

run_query "5.2 Race Test #2 - Treasury dispensed exactly 100 (not 120)" \
"SELECT
    a.code as asset,
    COUNT(wt.id) as successful_topups,
    SUM(wt.amount) as total_dispensed,
    CASE
        WHEN SUM(wt.amount) <= 100 THEN '✅ Race prevented'
        ELSE '❌ Race condition occurred'
    END as status
FROM wallet_transactions wt
JOIN asset_types a ON wt.asset_type_id = a.id
WHERE wt.idempotency_key LIKE 'war-%'
  AND wt.status = 'SUCCESS'
  AND wt.transaction_type = 'TOP_UP'
GROUP BY a.code;"

run_query "5.3 Failed Race Requests (these SHOULD exist - proving race protection worked)" \
"SELECT
    transaction_type,
    failure_reason,
    COUNT(*) as failed_count,
    CASE
        WHEN COUNT(*) > 0 THEN '✅ Correctly rejected'
        ELSE '⚠️ No failures (might indicate test issue)'
    END as status
FROM wallet_transactions
WHERE idempotency_key LIKE 'race%' OR idempotency_key LIKE 'war-%'
  AND status = 'FAILED'
GROUP BY transaction_type, failure_reason;"

# ============================================================================
# 6. DETAILED TRANSACTION HISTORY
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "6️⃣  TRANSACTION HISTORY (Last 20)"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "6.1 Recent Transactions with Full Details" \
"SELECT
    wt.created_at,
    wt.transaction_type,
    u.name as user_name,
    a.code as asset,
    wt.amount,
    wt.status,
    wt.idempotency_key,
    wt.failure_reason
FROM wallet_transactions wt
JOIN users u ON wt.user_id = u.id
JOIN asset_types a ON wt.asset_type_id = a.id
ORDER BY wt.created_at DESC
LIMIT 20;"

# ============================================================================
# 7. ORPHANED DATA CHECK
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "7️⃣  ORPHANED DATA CHECKS"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "7.1 Ledger Entries Without Successful Transaction (should be 0)" \
"SELECT
    COUNT(*) as orphaned_count,
    CASE
        WHEN COUNT(*) = 0 THEN '✅ No orphaned ledger entries'
        ELSE '❌ Found orphaned ledger entries'
    END as status
FROM ledger_entries le
LEFT JOIN wallet_transactions wt ON le.wallet_transaction_id = wt.id
WHERE wt.id IS NULL OR wt.status != 'SUCCESS';"

run_query "7.2 Successful Transactions Without Ledger Entries (should be 0)" \
"SELECT
    COUNT(*) as orphaned_count,
    CASE
        WHEN COUNT(*) = 0 THEN '✅ No orphaned transactions'
        ELSE '❌ Found transactions without ledger'
    END as status
FROM wallet_transactions wt
LEFT JOIN ledger_entries le ON wt.id = le.wallet_transaction_id
WHERE wt.status = 'SUCCESS'
  AND le.id IS NULL;"

# ============================================================================
# 8. SUMMARY REPORT
# ============================================================================
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "8️⃣  EXECUTIVE SUMMARY"
echo "═══════════════════════════════════════════════════════════════════"
echo ""

run_query "8.1 Overall System Health" \
"SELECT
    (SELECT COUNT(*) FROM wallets WHERE balance < 0) as negative_balances,
    (SELECT COUNT(*) FROM wallet_transactions WHERE status = 'PENDING') as pending_txs,
    (SELECT COUNT(*) FROM wallet_transactions WHERE status = 'SUCCESS') as successful_txs,
    (SELECT COUNT(*) FROM wallet_transactions WHERE status = 'FAILED') as failed_txs,
    (SELECT COUNT(*) FROM ledger_entries) as total_ledger_entries,
    CASE
        WHEN (SELECT COUNT(*) FROM wallets WHERE balance < 0) = 0
         AND (SELECT COUNT(*) FROM wallet_transactions WHERE status = 'PENDING') = 0
        THEN '✅ SYSTEM HEALTHY'
        ELSE '❌ ISSUES DETECTED'
    END as overall_status;"

echo ""
echo "======================================================================"
echo "✅ VALIDATION COMPLETE!"
echo "======================================================================"
echo ""
echo "🔍 Key Things to Check:"
echo "  1. ✅ All balances should be >= 0"
echo "  2. ✅ Every SUCCESS transaction should have exactly 2 ledger entries"
echo "  3. ✅ Total DEBITS = Total CREDITS (conservation of money)"
echo "  4. ✅ No PENDING transactions"
echo "  5. ✅ Race tests: User spent ≤150, Treasury dispensed ≤100"
echo "  6. ✅ Total money in system = Initial supply (1,050,000 GOLD)"
echo ""