$ErrorActionPreference = 'Stop'
$script:quoteValues = $false
function Read-EnvFile([string]$Path) {
    $values = @{}
    if (Test-Path $Path) {
        foreach ($line in Get-Content $Path) {
            if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
                $value = $Matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                $values[$Matches[1]] = $value
            }
        }
    }
    return $values
}
function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
function Add-Missing([string]$Key, [string]$Value) {
    if (-not $script:target.ContainsKey($Key)) {
        if ([string]::IsNullOrWhiteSpace($Value)) { throw "No value supplied for $Key" }
        # Literal dotenv values; dollar signs are not expanded by Compose inside single quotes.
        if ($Value.Contains("'") -or $Value.Contains("`r") -or $Value.Contains("`n")) {
            throw "Set $Key manually in .env: this script cannot quote its value safely."
        }
        if ($script:quoteValues) {
            $script:lines.Add("$Key='$Value'")
        } else {
            if ($Value -notmatch '^[A-Za-z0-9:/?&=._%@,+\-]+$') {
                throw "Set $Key manually: a value shared by Compose and Spring properties needs compatible escaping."
            }
            $script:lines.Add("$Key=$Value")
        }
        $script:target[$Key] = $Value
    } elseif ([string]::IsNullOrWhiteSpace($script:target[$Key])) {
        throw "Existing $Key in .env is empty; fill it before continuing."
    }
}
function Prefer-Source($Source, [string]$Key, [string]$Fallback) {
    if ($Source.ContainsKey($Key) -and -not [string]::IsNullOrWhiteSpace($Source[$Key])) { return $Source[$Key] }
    return $Fallback
}
function Save-AddedSettings([string]$Path) {
    if ($script:lines.Count -gt 0) {
        if (Test-Path $Path) {
            Copy-Item $Path ($Path + '.backup-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
        }
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        [IO.File]::AppendAllText($Path, "`r`n# Payment integration`r`n" + ($script:lines -join "`r`n") + "`r`n", $utf8)
    }
    Write-Host "Environment prepared. Added $($script:lines.Count) settings. Existing settings were retained."
}

$root = Split-Path $PSScriptRoot -Parent
$path = Join-Path $root '.env'
$script:target = Read-EnvFile $path
$script:lines = New-Object 'System.Collections.Generic.List[string]'
Add-Missing 'PAYMENT_PORT' '8084'
Add-Missing 'PAYMENT_MONGO_PORT' '27017'
Add-Missing 'PAYMENT_MONGO_DB' 'payment_service_db'
Add-Missing 'PAYMENT_MONGO_USER' 'payment_dev'
Add-Missing 'PAYMENT_MONGO_PASSWORD' (New-LocalPassword)
$user = [Uri]::EscapeDataString($script:target['PAYMENT_MONGO_USER'])
$password = [Uri]::EscapeDataString($script:target['PAYMENT_MONGO_PASSWORD'])
$db = [Uri]::EscapeDataString($script:target['PAYMENT_MONGO_DB'])
$port = $script:target['PAYMENT_MONGO_PORT']
Add-Missing 'PAYMENT_MONGODB_URI' "mongodb://${user}:${password}@localhost:${port}/${db}?authSource=admin&serverSelectionTimeoutMS=5000"
Add-Missing 'PAYMENT_MONGODB_URI_DOCKER' "mongodb://${user}:${password}@payment-mongodb:27017/${db}?authSource=admin&serverSelectionTimeoutMS=5000"
Add-Missing 'ORDER_SERVICE_URL' 'http://localhost:8083'
Add-Missing 'AUTH_JWKS_URI' 'http://localhost:8082/.well-known/jwks.json'
Add-Missing 'KAFKA_BOOTSTRAP_SERVERS' 'localhost:9092'
Add-Missing 'PAYMENT_EVENTS_TOPIC' 'payment-events'
Save-AddedSettings $path
