#!/bin/bash
set -e
T=/home/claude/tools
AAPT2=$T/aaptjs3-2.0.2/package/bin/x64/linux/aapt2
ANDROID_JAR=$T/ap/android-34/android.jar
DX=$(ls $T/dex2jar/*/lib/dx-30.0.2.jar)
rm -rf build && mkdir -p build/{res,gen,classes,dex}
$AAPT2 compile --dir res -o build/res/res.zip
$AAPT2 link -I $ANDROID_JAR --manifest AndroidManifest.xml -o build/base.apk --java build/gen build/res/res.zip --min-sdk-version 21 --target-sdk-version 34
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -cp $ANDROID_JAR -d build/classes $(find src build/gen -name "*.java") 2>&1 | grep -v "^warning\|bootstrap\|^1 warning" || true
ls build/classes/de/kopfnummer/app/MainActivity.class >/dev/null
java -jar $DX --dex --min-sdk-version=21 --output=build/dex/classes.dex build/classes
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
java -jar $T/uber-apk-signer.jar -a build/unsigned.apk -o build/out --allowResign > build/sign.log 2>&1 || { cat build/sign.log; exit 1; }
cp build/out/*.apk EquiTag.apk
ls -la EquiTag.apk
