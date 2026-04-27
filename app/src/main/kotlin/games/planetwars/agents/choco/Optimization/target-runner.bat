@echo off
setlocal

set attack=0.5
set targets=5
set defense=5.0
set territory=10.0
set shipdiff=10.0
set egrowth=3.0
set lgrowth=1.5
set etransit=0.3
set ltransit=0.8
set enemybonus=2.0
set neutralbonus=1.0
set ownbonus=0.1

REM Support both styles:
REM 1) Manual positional: 12 raw values
REM 2) iRace style: ... --param value (with optional control args before params)
if not "%~12"=="" if "%~13"=="" (
	set attack=%1
	set targets=%2
	set defense=%3
	set territory=%4
	set shipdiff=%5
	set egrowth=%6
	set lgrowth=%7
	set etransit=%8
	set ltransit=%9
	set enemybonus=%~10
	set neutralbonus=%~11
	set ownbonus=%~12
) else (
	:parse_args
	if "%~1"=="" goto done_parse

	if /I "%~1"=="--attackShipsFraction" (
		set attack=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--topTargetsPerSource" (
		set targets=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--minDefenseShips" (
		set defense=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--territoryWeight" (
		set territory=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--shipDiffDivisor" (
		set shipdiff=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--earlyGrowthWeight" (
		set egrowth=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--lateGrowthWeight" (
		set lgrowth=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--earlyTransitWeight" (
		set etransit=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--lateTransitWeight" (
		set ltransit=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--enemyTargetBonus" (
		set enemybonus=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--neutralTargetBonus" (
		set neutralbonus=%~2
		shift
		shift
		goto parse_args
	)
	if /I "%~1"=="--ownTargetBonus" (
		set ownbonus=%~2
		shift
		shift
		goto parse_args
	)

	for /f "tokens=1,2 delims==" %%A in ("%~1") do (
		if /I "%%~A"=="--attackShipsFraction" set attack=%%~B
		if /I "%%~A"=="--topTargetsPerSource" set targets=%%~B
		if /I "%%~A"=="--minDefenseShips" set defense=%%~B
		if /I "%%~A"=="--territoryWeight" set territory=%%~B
		if /I "%%~A"=="--shipDiffDivisor" set shipdiff=%%~B
		if /I "%%~A"=="--earlyGrowthWeight" set egrowth=%%~B
		if /I "%%~A"=="--lateGrowthWeight" set lgrowth=%%~B
		if /I "%%~A"=="--earlyTransitWeight" set etransit=%%~B
		if /I "%%~A"=="--lateTransitWeight" set ltransit=%%~B
		if /I "%%~A"=="--enemyTargetBonus" set enemybonus=%%~B
		if /I "%%~A"=="--neutralTargetBonus" set neutralbonus=%%~B
		if /I "%%~A"=="--ownTargetBonus" set ownbonus=%%~B
	)

	shift
	goto parse_args
)

:done_parse

set ROOT_DIR=%~dp0..\..\..\..\..\..\..\..\..
for %%I in ("%ROOT_DIR%") do set ROOT_DIR=%%~fI

set ARGS=%attack%,%targets%,%defense%,%territory%,%shipdiff%,%egrowth%,%lgrowth%,%etransit%,%ltransit%,%enemybonus%,%neutralbonus%,%ownbonus%
set SCORE=

for /f "delims=" %%S in ('call "%ROOT_DIR%\gradlew.bat" -q :app:runChocoIrace --args="%ARGS%" 2^>nul ^| findstr /R "^[0-9][0-9]*\.[0-9][0-9]*$"') do set SCORE=%%S

if not defined SCORE set SCORE=0.0

echo %SCORE%
endlocal