param(
    [string]$MavenRepository = "$env:USERPROFILE/.m2/repository",
    [string]$ClientJavaHome = '',
    [switch]$Tls
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$localRoot = Join-Path $projectRoot 'docs/dev-ops/ab-v2/.local'
$appJar = Join-Path $projectRoot 'group-buy-market-app/target/group-buy-market-app.jar'
$aJava = (Get-Command java).Source
$previousEnvironment = @{}
$provider = $null

function Set-TaskEnvironment([string]$name, [string]$value) {
    if (-not $previousEnvironment.ContainsKey($name)) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
function Import-TaskEnvironment([string]$path) {
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { Set-TaskEnvironment $Matches[1] $Matches[2] }
    }
}
function Test-TaskPort([int]$port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $pending = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(500)) { return $false }
        $client.EndConnect($pending); return $true
    } catch { return $false } finally { $client.Dispose() }
}

Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath $appJar)) { throw 'Build the A application before running smoke tests.' }
    if (-not (Test-Path -LiteralPath "$localRoot/app.env") -or -not (Test-Path -LiteralPath "$localRoot/b.env")) {
        throw 'Run the ab-v2 development environment setup first.'
    }
    if (Test-TaskPort 50051) { throw 'Port 50051 is already occupied; stop the existing test provider first.' }
    foreach ($port in @(13306,16379,9876)) { if (-not (Test-TaskPort $port)) { throw "Required development service is unavailable on port $port." } }
    Import-TaskEnvironment "$localRoot/app.env"
    Set-TaskEnvironment 'AB_V2_TEST_A_ACCESS_KEY' $env:AB_V2_ROCKETMQ_ACCESS_KEY
    Set-TaskEnvironment 'AB_V2_TEST_A_SECRET_KEY' $env:AB_V2_ROCKETMQ_SECRET_KEY
    $config = Get-Content 'group-buy-market-app/src/main/resources/application-dev.yml' -Raw
    $dbPassword = [regex]::Match($config, '(?m)^\s+password:\s*([^\r\n]+)').Groups[1].Value.Trim().Trim('"', "'")
    if ($env:PHASE1_DB_PASSWORD) { $dbPassword = $env:PHASE1_DB_PASSWORD }
    Set-TaskEnvironment 'PHASE1_DB_PASSWORD' $dbPassword
    Set-TaskEnvironment 'SPRING_DATASOURCE_PASSWORD' $dbPassword
    Set-TaskEnvironment 'SPRING_DATASOURCE_URL' 'jdbc:mysql://127.0.0.1:13306/group_buy_market_phase1_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
    $providerArguments = @('-jar',$appJar,'--server.port=18091')
    if ($Tls) {
        if (-not $ClientJavaHome) { throw 'TLS tests require an existing JDK 17+ ClientJavaHome.' }
        $tlsRoot = Join-Path $localRoot 'tls'
        & (Join-Path $ClientJavaHome 'bin/java.exe') docs/tests/ab-v2-client/CreateTestCertificate.java $tlsRoot
        if ($LASTEXITCODE -ne 0) { throw 'Local test certificate creation failed.' }
        Set-TaskEnvironment 'AB_V2_TEST_TLS_CERT' (Join-Path $tlsRoot 'certificate.pem')
        Set-TaskEnvironment 'AB_V2_TEST_TLS_KEY' (Join-Path $tlsRoot 'private-key.pem')
        Set-TaskEnvironment 'AB_V2_TLS_SERVER_CERT' $env:AB_V2_TEST_TLS_CERT
        Set-TaskEnvironment 'AB_V2_TLS_SERVER_KEY' $env:AB_V2_TEST_TLS_KEY
        Set-TaskEnvironment 'AB_V2_TLS_CLIENT_CA' $env:AB_V2_TEST_TLS_CERT
        Set-TaskEnvironment 'DUBBO_PROTOCOL_SSL_ENABLED' 'true'
        Set-TaskEnvironment 'DUBBO_SSL_SERVER_KEY_CERT_CHAIN_PATH' $env:AB_V2_TEST_TLS_CERT
        Set-TaskEnvironment 'DUBBO_SSL_SERVER_PRIVATE_KEY_PATH' $env:AB_V2_TEST_TLS_KEY
        Set-TaskEnvironment 'DUBBO_SSL_SERVER_TRUST_CERT_COLLECTION_PATH' $env:AB_V2_TEST_TLS_CERT
        Set-TaskEnvironment 'AB_V2_RPC_REQUIRE_TLS' 'true'
        $providerArguments += '--spring.profiles.active=dev,v2-tls'
    }
    $provider = Start-Process -FilePath $aJava -ArgumentList $providerArguments -WindowStyle Hidden -PassThru `
        -WorkingDirectory $localRoot -RedirectStandardOutput "$localRoot/provider.stdout.log" -RedirectStandardError "$localRoot/provider.stderr.log"
    $readyBy = [DateTime]::UtcNow.AddSeconds(60)
    while (-not (Test-TaskPort 50051)) {
        if ($provider.HasExited) { throw 'A provider exited; inspect the ignored provider logs.' }
        if ([DateTime]::UtcNow -ge $readyBy) { throw 'A provider did not open port 50051 within 60 seconds.' }
        Start-Sleep -Milliseconds 500
    }
    Import-TaskEnvironment "$localRoot/b.env"
    if ($Tls) { Set-TaskEnvironment 'AB_V2_RPC_REQUIRE_TLS' 'true' }
    & mvn -q -pl group-buy-market-app -am "-Dmaven.repo.local=$MavenRepository" '-DskipTests=false' '-DfailIfNoTests=false' `
        '-Dtest=V2DubboSmokeTest,V2MqSmokeTest' test *> "$localRoot/live-smoke.log"
    if ($LASTEXITCODE -ne 0) { throw 'Live Triple/MQ smoke tests failed; inspect .local/live-smoke.log.' }
    if ($ClientJavaHome) {
        Set-TaskEnvironment 'JAVA_HOME' $ClientJavaHome
        & mvn -q -f docs/tests/ab-v2-client/pom.xml "-Dmaven.repo.local=$MavenRepository" test *> "$localRoot/boot4-client.log"
        if ($LASTEXITCODE -ne 0) { throw 'Boot 4 consumer compatibility tests failed; inspect .local/boot4-client.log.' }
    }
    Write-Output 'Live Triple/MQ smoke tests passed; the temporary isolated provider will be stopped.'
} finally {
    if ($provider -and -not $provider.HasExited) { Stop-Process -Id $provider.Id -Force }
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
}
