@ECHO OFF

ECHO "Cleaning Build"

gradlew clean

ECHO "Deploying Nodes....."

gradlew.bat deployNodes

ECHO "Nodes Deployed, Running the Ndoes .."

build/nodes/runnodes.bat

nodeNOTARY=`netstat -ano | findStr "10043" | findStr "LISTENING"`
nodeXYZ=`netstat -ano | findStr "10046" | findStr "LISTENING"`
nodeBANK=`netstat -ano | findStr "10049" | findStr "LISTENING"`
nodeFA=`netstat -ano | findStr "10053" | findStr "LISTENING"`

echo %nodeNOTARY%