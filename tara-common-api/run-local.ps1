# 로컬 실행: .env.local 의 KEY=VALUE 를 환경변수로 올린 뒤 bootRun
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path .env.local)) { throw ".env.local 이 없습니다. .env.example 을 복사해 만드세요." }
Get-Content .env.local -Encoding utf8 | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq '' -or $line.StartsWith('#')) { return }
  $i = $line.IndexOf('=')
  if ($i -lt 1) { return }
  $k = $line.Substring(0, $i).Trim(); $v = $line.Substring($i + 1).Trim()
  Set-Item -Path "env:$k" -Value $v
}
if ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) { $env:SPRING_PROFILES_ACTIVE = 'local' }
if ($env:SPRING_PROFILES_ACTIVE -eq 'dev' -and $env:DB_HOST -ne 'localhost' -and $env:DB_HOST -ne '127.0.0.1') {
  throw "DB_HOST 가 '$($env:DB_HOST)' 입니다. 로컬 실행은 localhost 만 허용합니다 (운영 DB 보호)."
}
Write-Host "[run-local] profile=$($env:SPRING_PROFILES_ACTIVE)  port=$($env:SERVER_PORT)"
& .\gradlew.bat bootRun
