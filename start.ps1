# ===============================================================
#  Anonymous Student Review System  -  ONE command to start.
#
#      .\start.ps1              (runs on http://localhost:8080)
#      .\start.ps1 -Port 9090   (or choose another port)
#
#  This script compiles the code and starts the server. That's it.
# ===============================================================
param([int]$Port = 8080)

$ErrorActionPreference = "Stop"

# SQLite driver (the only external dependency) must exist in lib\.
$sqlite = "lib\sqlite-jdbc-3.46.1.3.jar"
if (-not (Test-Path $sqlite)) {
    Write-Host "Missing SQLite driver: $sqlite" -ForegroundColor Red
    Write-Host "Download it into lib\ (see README.md, Step 1)." -ForegroundColor Yellow
    exit 1
}

# The SLF4J jars (optional, only silence driver logging) are bundled too.
$cpParts = @($sqlite)
foreach ($j in @("lib\slf4j-api-2.0.16.jar", "lib\slf4j-simple-2.0.16.jar")) {
    if (Test-Path $j) { $cpParts += $j }
}
$cp = ($cpParts -join ";")

# --- Compile ---------------------------------------------------
if (-not (Test-Path out)) { New-Item -ItemType Directory -Path out | Out-Null }
Write-Host "[1/2] Compiling..." -ForegroundColor Cyan
$files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
javac -d out -cp $cp $files
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed." -ForegroundColor Red
    exit 1
}

# --- Run -------------------------------------------------------
Write-Host "[2/2] Starting server on http://localhost:$Port" -ForegroundColor Green
Write-Host "      Press Ctrl+C to stop." -ForegroundColor DarkGray
java "-DPORT=$Port" -cp "out;$cp" com.reviewapp.server.Main
