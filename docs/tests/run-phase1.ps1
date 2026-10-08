param(
    [string]$MavenRepository = "$env:USERPROFILE\.m2\repository",
    [string]$TestSelector = 'PhaseOne*Test,ReauditTradeTest'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$previousMysqlPassword = $env:MYSQL_PWD
$previousTestPassword = $env:PHASE1_DB_PASSWORD
Push-Location $projectRoot
try {
    if (-not $env:PHASE1_DB_PASSWORD) {
        $config = Get-Content 'group-buy-market-app/src/main/resources/application-dev.yml' -Raw
        $env:PHASE1_DB_PASSWORD = [regex]::Match($config, '(?m)^\s+password:\s*([^\r\n]+)').Groups[1].Value.Trim().Trim('"', "'")
    }
    if (-not $env:PHASE1_DB_PASSWORD) { throw 'Set PHASE1_DB_PASSWORD before running the tests.' }
    $env:MYSQL_PWD = $env:PHASE1_DB_PASSWORD
    # Copy structure only. The test suite writes exclusively to this named test schema.
    $schemaSql = 'CREATE DATABASE IF NOT EXISTS group_buy_market_phase1_test CHARACTER SET utf8mb4;'
    $tables = @('group_buy_activity','group_buy_discount','sku','sc_sku_activity',
        'group_buy_order','group_buy_order_list','notify_task','crowd_tags','crowd_tags_detail',
        'crowd_tags_job','trade_request_guard','trade_quota_guard',
        'ab_product_projection','ab_team','ab_trade_order','ab_payment_guard','ab_trade_guard',
        'ab_inbox','ab_outbox','ab_waiting_dependency','ab_quarantine','ab_exception_payment')
    foreach ($table in $tables) {
        $schemaSql += " CREATE TABLE IF NOT EXISTS group_buy_market_phase1_test.$table LIKE group_buy_market.$table;"
    }
    & mysql --host=127.0.0.1 --port=13306 --user=root --execute=$schemaSql
    if ($LASTEXITCODE -ne 0) { throw 'Test schema setup failed. Apply the phase-one migration to the development schema first.' }
    # CREATE IF NOT EXISTS does not widen an older isolated table after the v2 migration.
    # Inspect first, and alter only the two missing shared ID capacities in this test schema.
    foreach ($column in @(@('sc_sku_activity','goods_id'),@('crowd_tags_detail','user_id'))) {
        $capacitySql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE TABLE_SCHEMA='group_buy_market_phase1_test' AND TABLE_NAME='$($column[0])' AND COLUMN_NAME='$($column[1])';"
        $capacity = & mysql --host=127.0.0.1 --port=13306 --user=root --batch --skip-column-names --execute=$capacitySql
        if ($LASTEXITCODE -ne 0 -or -not $capacity) { throw 'Could not verify isolated v2 ID capacity.' }
        if ([int]$capacity -lt 64) {
            & mysql --host=127.0.0.1 --port=13306 --user=root --execute="ALTER TABLE group_buy_market_phase1_test.$($column[0]) MODIFY $($column[1]) varchar(64) NOT NULL;"
            if ($LASTEXITCODE -ne 0) { throw 'Could not widen isolated v2 ID column.' }
        }
    }
    & mvn "-Dmaven.repo.local=$MavenRepository" '-DskipTests=false' "-Dtest=$TestSelector" '-DfailIfNoTests=false' package
    if ($LASTEXITCODE -ne 0) { throw 'Phase-one tests or packaging failed; inspect target/surefire-reports.' }
} finally {
    $env:MYSQL_PWD = $previousMysqlPassword
    $env:PHASE1_DB_PASSWORD = $previousTestPassword
    Pop-Location
}
