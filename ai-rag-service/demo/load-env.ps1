# Dot source: . .\demo\load-env.ps1  (from project root)
$path = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path $path)) { throw "Missing .env. Copy .env.example to .env first." }
Get-Content $path -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Length -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
        }
    }
}
Write-Host 'Loaded environment variables into this PowerShell session.'
