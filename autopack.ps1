$tempDir = Join-Path $PSScriptRoot "_pyibuild_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
pyinstaller --onefile --windowed `
  --distpath . `
  --workpath "$tempDir\build" `
  --specpath "$tempDir\spec" `
  --clean --noconfirm `
  --name "ReBiliLiveNotification" `
  ReBiliLiveNotificationGui.py
Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
