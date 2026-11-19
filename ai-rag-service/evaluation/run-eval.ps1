param(
    [string]$BaseUrl = "http://localhost:8083"
)

Write-Host "Starting evaluation runner with Python..."
python "$PSScriptRoot\evaluate.py" $BaseUrl
