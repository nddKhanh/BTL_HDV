param([string]$BaseUrl = 'http://localhost:8083')
if (-not $env:ADMIN_API_KEY) { throw 'Set ADMIN_API_KEY environment variable first' }
$headers = @{ 'X-Admin-Key' = $env:ADMIN_API_KEY }
$docs = Get-Content -Raw -Encoding UTF8 "$PSScriptRoot\faq-sample.json" | ConvertFrom-Json
foreach ($d in $docs) {
  $json = $d | ConvertTo-Json -Depth 4 -Compress
  $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/knowledge/documents" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  Write-Host "Saved $($res.sourceId)"
}
