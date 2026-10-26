set -e; cd /workspace/shieldhome; B=~/android-sdk/build-tools/35.0.0; P=~/android-sdk/platforms/android-30/android.jar
rm -rf out && mkdir -p out/cls out/dex && $B/aapt2 compile --dir res -o out/res.zip && $B/aapt2 link -o out/base.apk -I $P --manifest AndroidManifest.xml out/res.zip --java out/gen
javac -g:none -source 8 -target 8 -nowarn -cp $P -d out/cls $(find src out/gen -name '*.java') 2>&1 | grep -v -i -e warn -e Note || true
(cd out/cls && jar cf ../c.jar .); $B/d8 --release --min-api 26 --lib $P --output out/dex out/c.jar
cp out/base.apk out/u.apk; (cd out/dex && zip -q ../u.apk classes.dex); $B/zipalign -f 4 out/u.apk out/a.apk; $B/apksigner sign --ks key.jks --ks-pass REDACTED --out /workspace/shieldhome.apk out/a.apk; ls -la /workspace/shieldhome.apk
