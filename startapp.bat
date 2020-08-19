@ECHO OFF

ECHO "Cleaning Build"

CALL gradlew clean

ECHO "Deploying Nodes....."

CALL gradlew.bat deployNodes

ECHO "Nodes Deployed, Running the Ndoes .."

CALL build/nodes/runnodes.bat >NUL

set /A COUNTER=0
set /A MAX_COUNTER=300

:NOTARY_START_CHECK
CALL netstat -ano | findStr "10053" | findStr "LISTENING" >NUL
if %ERRORLEVEL% equ 0 goto STARTED

set /A COUNTER=%COUNTER%+10
ping -n 10 127.0.0.1>nul
if %COUNTER%  gtr %MAX_COUNTER% goto ERROR_AND_EXIT
goto NOTARY_START_CHECK

:STARTED
echo NOTARY STARTED



:FA_NODE_START_CHECK
CALL netstat -ano | findStr "10046" | findStr "LISTENING" >NUL
if %ERRORLEVEL% equ 0 goto FA_STARTED

set /A COUNTER=%COUNTER%+10
ping -n 10 127.0.0.1>nul
if %COUNTER%  gtr %MAX_COUNTER% goto ERROR_AND_EXIT
goto FA_NODE_START_CHECK

:FA_STARTED
echo FA STARTED



:BANK_NODE_START_CHECK
CALL netstat -ano | findStr "10049" | findStr "LISTENING" >NUL
if %ERRORLEVEL% equ 0 goto BANK_NODE_STARTED

set /A COUNTER=%COUNTER%+10
ping -n 10 127.0.0.1>nul
if %COUNTER%  gtr %MAX_COUNTER% goto ERROR_AND_EXIT
goto BANK_NODE_START_CHECK

:BANK_NODE_STARTED
echo NOTARY STARTED



:CA_NODE_START_CHECK
CALL netstat -ano | findStr "10053" | findStr "LISTENING" >NUL
if %ERRORLEVEL% equ 0 goto CA_NODE_STARTED

set /A COUNTER=%COUNTER%+10
ping -n 10 127.0.0.1>nul
if %COUNTER%  gtr %MAX_COUNTER% goto ERROR_AND_EXIT
goto CA_NODE_START_CHECK

:CA_NODE_STARTED
echo CA NODE STARTED


echo "::::::::::::::::ALL APPS AND SERVER STARTED::::::::::::::"
exit /B 0

:ERROR_AND_EXIT
echo "StartUp taking too much time"
exit /B -1

