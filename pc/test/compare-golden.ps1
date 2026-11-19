# 黄金表回归：编译 GoldenDump → 跑出实际行为 → 与 docs/GOLDEN_BUTTONS.tsv 逐行比对
#
# 用法： pwsh -File D:\Projects\PhoneMouse\pc\test\compare-golden.ps1
# 退出码：0 = 完全一致；1 = 有差异或编译失败

$ErrorActionPreference = 'Stop'

$pc     = Split-Path -Parent $PSScriptRoot
$root   = Split-Path -Parent $pc
$javac  = 'D:\Tools\jdk-17\bin\javac.exe'
$java   = 'D:\Tools\jdk-17\bin\java.exe'
$out    = Join-Path $pc 'out'
$gout   = Join-Path $pc 'out-golden'
$golden = Join-Path $root 'docs\GOLDEN_BUTTONS.tsv'

if (-not (Test-Path $out)) {
    Write-Host "先编译主程序： pc\build.bat" -ForegroundColor Red
    exit 1
}

if (Test-Path $gout) { Remove-Item -Recurse -Force $gout }
New-Item -ItemType Directory -Force -Path $gout | Out-Null

& $javac -encoding UTF-8 -cp $out -d $gout (Join-Path $PSScriptRoot 'GoldenDump.java')
if ($LASTEXITCODE -ne 0) { Write-Host 'GoldenDump 编译失败' -ForegroundColor Red; exit 1 }

$actual = & $java -cp "$out;$gout" golden.GoldenDump 2>$null | Where-Object { $_ -match '^[TC]\t' }
$expect = Get-Content -LiteralPath $golden -Encoding UTF8 |
    Where-Object { $_ -and -not $_.StartsWith('#') }

$diff = Compare-Object -ReferenceObject $expect -DifferenceObject $actual
if ($diff) {
    Write-Host '黄金表不一致：' -ForegroundColor Red
    $diff | ForEach-Object {
        $side = if ($_.SideIndicator -eq '=>') { '实际' } else { '基准' }
        Write-Host ("  [{0}] {1}" -f $side, $_.InputObject)
    }
    exit 1
}

Write-Host ("GOLDEN OK  ({0} 行一致)" -f $expect.Count) -ForegroundColor Green
exit 0
