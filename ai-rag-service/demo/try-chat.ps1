param([string]$BaseUrl = 'http://localhost:8083')
if (-not $env:ADMIN_API_KEY) { throw 'Set ADMIN_API_KEY environment variable first' }
Write-Host 'Reindexing FAQ + Catalog C5...'
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/knowledge/reindex" -Headers @{ 'X-Admin-Key'=$env:ADMIN_API_KEY } -ContentType 'application/json' -Body '{}' | Format-List
foreach ($question in @('Áo thun basic làm bằng chất liệu gì?', 'Chính sách đổi trả như thế nào?', 'Balo có ngăn laptop không?', 'Còn hàng không?')) {
  Write-Host "Question: $question"
  $json = @{ question = $question } | ConvertTo-Json -Compress
  Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/chat" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) | ConvertTo-Json -Depth 8
}
