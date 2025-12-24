# Script cap nhat IP server nhanh
# Su dung: .\CAP-NHAT-IP-NHANH.ps1

$serverIP = "192.168.50.129"

Write-Host "========================================" -ForegroundColor Yellow
Write-Host "  CAP NHAT IP SERVER NHANH" -ForegroundColor Yellow
Write-Host "  RTP AV Conference" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""

Write-Host "[INFO] Dang cap nhat IP: $serverIP" -ForegroundColor Cyan
Write-Host ""

$files = Get-ChildItem -Path "." -Filter "START-CLIENT-*.bat"

if ($files.Count -eq 0) {
    Write-Host "[ERROR] Khong tim thay file START-CLIENT-*.bat!" -ForegroundColor Red
    Write-Host ""
    pause
    exit 1
}

$count = 0
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $newContent = $content -replace 'set SERVER_IP=.*', "set SERVER_IP=$serverIP"
    
    if ($content -ne $newContent) {
        Set-Content -Path $file.FullName -Value $newContent -NoNewline
        Write-Host "[SUCCESS] Da cap nhat: $($file.Name)" -ForegroundColor Green
        $count++
    } else {
        Write-Host "[INFO] Khong can cap nhat: $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "  [HOAN TAT] Da cap nhat $count file(s)!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "[THONG TIN]" -ForegroundColor Cyan
Write-Host "  IP LAN moi: $serverIP" -ForegroundColor White
Write-Host "  So file da cap nhat: $count" -ForegroundColor White
Write-Host ""
Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

