$tempDir = Join-Path $PSScriptRoot "_pyibuild_$(Get-Date -Format 'yyyyMMdd_HHmmss')"

python -B -c "from icon_module import create_microphone_icon; img = create_microphone_icon(); img.save('ReBiliLiveNotification.ico', format='ICO', sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])"

pyinstaller --onefile --windowed `
  --distpath . `
  --workpath "$tempDir\build" `
  --specpath "$tempDir\spec" `
  --clean --noconfirm `
  --icon "$PSScriptRoot\ReBiliLiveNotification.ico" `
  --add-data "$PSScriptRoot\ReBiliLiveNotification.ico;." `
  --collect-all PIL `
  --hidden-import PIL._tkinter_finder `
  --collect-all tkinter `
  --name "ReBiliLiveNotification" `
  ReBiliLiveNotificationGui.py

Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
Remove-Item "ReBiliLiveNotification.ico" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "__pycache__" -ErrorAction SilentlyContinue
