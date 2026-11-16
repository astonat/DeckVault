$ErrorActionPreference = "Stop"

$AndroidRoot = Join-Path $env:LOCALAPPDATA "Android"
$SdkDir = Join-Path $AndroidRoot "Sdk"
$JdkDir = Join-Path $AndroidRoot "tools\jdk-21"
$GradleBin = Join-Path $AndroidRoot "tools\gradle-8.14\bin\gradle.bat"

$env:JAVA_HOME = $JdkDir
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
$env:PATH = "$JdkDir\bin;$SdkDir\platform-tools;$env:PATH"

$ProjectDir = $PSScriptRoot
Set-Location $ProjectDir

# Ensure local.properties points to SDK
$escapedSdk = $SdkDir.Replace("\", "\\")
Set-Content -Path (Join-Path $ProjectDir "local.properties") -Value "sdk.dir=$escapedSdk" -Encoding ASCII

# Generate local release signing keystore if missing
$Keystore = Join-Path $ProjectDir "app\deckvault-release.jks"
if (-not (Test-Path $Keystore)) {
    Write-Host "[1/3] Generating local release signing keystore..."
    & "$JdkDir\bin\keytool.exe" -genkeypair `
        -v -keystore $Keystore `
        -storepass "deckvault_local_key" `
        -keypass "deckvault_local_key" `
        -alias "deckvault" `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=DeckVault, OU=Mobile, O=DeckVault, L=Local, S=Local, C=US"
}

# Generate Gradle wrapper if missing
if (-not (Test-Path (Join-Path $ProjectDir "gradlew.bat"))) {
    Write-Host "[2/3] Generating Gradle wrapper 8.14..."
    & $GradleBin --no-daemon wrapper --gradle-version 8.14
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "[3/3] Building Debug & Release APKs..."
& .\gradlew.bat --no-daemon assembleDebug assembleRelease
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "BUILD SUCCEEDED!"
Get-ChildItem (Join-Path $ProjectDir "app\build\outputs\apk") -Recurse -Filter "*.apk" | Select-Object FullName, Length
