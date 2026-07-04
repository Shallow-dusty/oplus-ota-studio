# Root Package Notes

This repository does not redistribute OTA ZIP files. The current OnePlus 9 Pro
CN full package was downloaded outside the repository for local root-related
work.

## OnePlus 9 Pro CN Current Full Package

- Device: `LE2120` / `OnePlus9Pro_CH`
- Display version: `LE2120_14.0.0.1901(CN01)`
- OTA build property: `LE2120_11.H.23_3230_202504181723`
- Local file:
  `E:\coding\oplus-ota-studio-downloads\LE2120_14.0.0.1901_CN01_full.zip`
- Size: `6559817109` bytes
- MD5: `5ae1e4d8101218d58c1da10092b22996`

Verify before using it:

```powershell
$file = 'E:\coding\oplus-ota-studio-downloads\LE2120_14.0.0.1901_CN01_full.zip'
Get-Item -LiteralPath $file | Select-Object FullName, Length, LastWriteTime
Get-FileHash -LiteralPath $file -Algorithm MD5
```

Evidence:
[`docs/evidence/download/current-full-package-oneplus9pro-cn-2026-07-02.txt`](evidence/download/current-full-package-oneplus9pro-cn-2026-07-02.txt).

## Boundary

The hash above only confirms that the local ZIP matches the metadata returned
by the live OTA endpoint. This project has not implemented OPlus package
signature verification.
