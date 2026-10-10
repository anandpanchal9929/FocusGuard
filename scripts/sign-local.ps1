param([Parameter(Mandatory=$true)][string]$UnsignedApk,[Parameter(Mandatory=$true)][string]$KeyStore,[Parameter(Mandatory=$true)][string]$SdkRoot,[string]$Alias='focusguard',[string]$OutputApk='FocusGuard-v0.1.0.apk',[string]$ProtectedPasswordFile)
$ErrorActionPreference='Stop'
$securePassword=if($ProtectedPasswordFile) { (Get-Content -Raw -LiteralPath $ProtectedPasswordFile).Trim() | ConvertTo-SecureString } else { Read-Host 'Keystore password' -AsSecureString }
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:FOCUSGUARD_SIGN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    & (Join-Path $SdkRoot 'build-tools\35.0.0\apksigner.bat') sign --ks $KeyStore --ks-key-alias $Alias --ks-pass env:FOCUSGUARD_SIGN_PASSWORD --key-pass env:FOCUSGUARD_SIGN_PASSWORD --out $OutputApk $UnsignedApk
    if($LASTEXITCODE -ne 0) { throw 'Signing failed' }
    & (Join-Path $SdkRoot 'build-tools\35.0.0\apksigner.bat') verify --verbose $OutputApk
    if($LASTEXITCODE -ne 0) { throw 'Signature verification failed' }
    Get-FileHash -Algorithm SHA256 -LiteralPath $OutputApk
} finally {
    Remove-Item Env:\FOCUSGUARD_SIGN_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
