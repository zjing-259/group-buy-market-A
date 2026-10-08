$ErrorActionPreference = 'Stop'
$work = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Path $work -Force | Out-Null
$secretFile = Join-Path $work 'secrets.json'
if (Test-Path -LiteralPath $secretFile) {
    $secrets = Get-Content -LiteralPath $secretFile -Raw | ConvertFrom-Json
} else {
    $secrets = [pscustomobject]@{
        admin = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        a = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        b = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        rpc = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        msgA = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        msgB = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        legacy = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    }
    $secrets | ConvertTo-Json | Set-Content -LiteralPath $secretFile -Encoding utf8
}
foreach ($name in @('rpc','msgA','msgB','legacy')) {
    if (-not $secrets.PSObject.Properties[$name]) {
        $secrets | Add-Member -NotePropertyName $name -NotePropertyValue ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    }
}
$secrets | ConvertTo-Json | Set-Content -LiteralPath $secretFile -Encoding utf8
$acl = @"
globalWhiteRemoteAddresses: []
accounts:
  - accessKey: ABDevAdmin
    secretKey: $($secrets.admin)
    admin: true
    defaultTopicPerm: PUB|SUB
    defaultGroupPerm: PUB|SUB
  - accessKey: ABDevAppA
    secretKey: $($secrets.a)
    admin: false
    defaultTopicPerm: DENY
    defaultGroupPerm: DENY
    topicPerms:
      - B_PRODUCT_EVENTS-dev=SUB
      - B_TRADE_EVENTS-dev=SUB
      - A_TEAM_EVENTS-dev=PUB
      - A_INTEGRATION_RESULTS-dev=PUB
      - "%DLQ%A_V2_RETRY_TEST-dev=SUB"
    groupPerms:
      - A_PRODUCT_SYNC-dev=PUB|SUB
      - A_TRADE_FACTS-dev=PUB|SUB
      - A_V2_RETRY_TEST-dev=PUB|SUB
      - A_V2_DLQ_TEST-dev=SUB
  - accessKey: ABDevAppB
    secretKey: $($secrets.b)
    admin: false
    defaultTopicPerm: DENY
    defaultGroupPerm: DENY
    topicPerms:
      - B_PRODUCT_EVENTS-dev=PUB
      - B_TRADE_EVENTS-dev=PUB
      - A_TEAM_EVENTS-dev=SUB
      - A_INTEGRATION_RESULTS-dev=SUB
    groupPerms:
      - B_TEAM_EVENTS-dev=PUB|SUB
      - B_INTEGRATION_RESULTS-dev=PUB|SUB
"@
$acl | Set-Content -LiteralPath (Join-Path $work 'plain_acl.yml') -Encoding utf8
@"
accessKey: ABDevAdmin
secretKey: $($secrets.admin)
"@ | Set-Content -LiteralPath (Join-Path $work 'tools.yml') -Encoding utf8
@"
AB_V2_ENABLED=true
AB_V2_RPC_CLIENT_ID=zj-market
AB_V2_RPC_KEY_ID=dev-rpc-1
AB_V2_RPC_SECRET=$($secrets.rpc)
AB_V2_RPC_SOURCE=s01
AB_V2_RPC_CHANNEL=c01
AB_V2_RPC_REQUIRE_TLS=false
AB_V2_ROCKETMQ_ACCESS_KEY=ABDevAppA
AB_V2_ROCKETMQ_SECRET_KEY=$($secrets.a)
AB_V2_MQ_A_KEY_ID=dev-a-msg-1
AB_V2_MQ_A_SECRET=$($secrets.msgA)
AB_V2_MQ_B_KEY_ID=dev-b-msg-1
AB_V2_MQ_B_SECRET=$($secrets.msgB)
AB_V2_LEGACY_HTTP_TOKEN=$($secrets.legacy)
AB_V2_LEGACY_NEW_ORDERS_ENABLED=false
"@ | Set-Content -LiteralPath (Join-Path $work 'app.env') -Encoding utf8
@"
AB_V2_RPC_CLIENT_ID=zj-market
AB_V2_RPC_KEY_ID=dev-rpc-1
AB_V2_RPC_SECRET=$($secrets.rpc)
AB_V2_ROCKETMQ_ACCESS_KEY=ABDevAppB
AB_V2_ROCKETMQ_SECRET_KEY=$($secrets.b)
AB_V2_MQ_A_KEY_ID=dev-a-msg-1
AB_V2_MQ_A_SECRET=$($secrets.msgA)
AB_V2_MQ_B_KEY_ID=dev-b-msg-1
AB_V2_MQ_B_SECRET=$($secrets.msgB)
AB_V2_LEGACY_HTTP_TOKEN=$($secrets.legacy)
"@ | Set-Content -LiteralPath (Join-Path $work 'b.env') -Encoding utf8
$env:DOCKER_CONFIG = Join-Path $env:TEMP 'codex-docker-config'
New-Item -ItemType Directory -Path $env:DOCKER_CONFIG -Force | Out-Null
docker compose -f (Join-Path $PSScriptRoot 'docker-compose.yml') up -d
if ($LASTEXITCODE -ne 0) { throw 'RocketMQ startup failed' }
$ready = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    docker exec ab-v2-broker-1 sh mqadmin topicList -n namesrv:9876 *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw 'RocketMQ Broker did not become ready.' }
foreach ($topic in @('B_PRODUCT_EVENTS-dev','B_TRADE_EVENTS-dev','A_TEAM_EVENTS-dev','A_INTEGRATION_RESULTS-dev')) {
    docker exec ab-v2-broker-1 sh mqadmin updateTopic -n namesrv:9876 -c ABDevCluster -t $topic -r 4 -w 4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "RocketMQ topic creation failed: $topic" }
}
