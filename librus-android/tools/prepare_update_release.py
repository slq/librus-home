"""Create public update metadata and corresponding sources; never reads a signing key or account state."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess,zipfile
project=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument("--apk",required=True);p.add_argument("--notes",required=True);args=p.parse_args()
apk=Path(args.apk).resolve();sdk=project/".tools/android-sdk/build-tools/35.0.0"
result=subprocess.run([str(sdk/"aapt.exe"),"dump","badging",str(apk)],capture_output=True,text=True,encoding="utf-8",errors="replace",check=True)
match=re.search(r"package: name='pl.librushome.android' versionCode='([0-9]+)' versionName='([0-9]+\.[0-9]+\.[0-9]+)'",result.stdout)
if not match:raise SystemExit("Nie rozpoznano wersji APK")
code,name=match.groups();expected="LibrusApp-android-"+name+".apk"
if apk.name!=expected or "application-debuggable" in result.stdout:raise SystemExit("Wydanie musi być poprawnie nazwanym APK release")
notes=Path(args.notes).read_text(encoding="utf-8-sig").strip()
if len(notes)>16000:raise SystemExit("Opis zmian jest za długi")
metadata=dict(schema=1,packageName="pl.librushome.android",versionCode=int(code),versionName=name,apkUrl="https://github.com/slq/librus-home/releases/download/android-v"+name+"/"+expected,sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),size=apk.stat().st_size,notes=notes)
(apk.parent/"librus-android-update.json").write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
# Sources reflect the working copy used for this build, including uncommitted new modules.
repo=project.parent
listing=subprocess.run(["git","ls-files","--cached","--others","--exclude-standard"],cwd=repo,capture_output=True,text=True,encoding="utf-8",check=True).stdout.splitlines()
source=apk.parent/("LibrusApp-android-"+name+"-source.zip")
allowed_suffixes={".java",".py",".md",".xml",".kts",".properties",".ps1",".txt",".bat",".jar"}
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as out:
    for relative in listing:
        f=repo/relative
        if not f.is_file():continue
        if not (relative.startswith("librus-android/") or relative in ["README.md","LICENSE",".gitignore"]):continue
        lower=relative.lower()
        if any(x in lower.split("/") for x in [".tools","artifacts","build",".git",".gradle"]):continue
        if f.name in ["signing.properties","local.properties"] or f.suffix.lower() in [".aes",".p12",".pfx",".key",".jks",".keystore"]:continue
        if f.suffix.lower() not in allowed_suffixes and f.name not in ["gradlew",".gitignore","LICENSE"]:continue
        out.write(f,relative)
print("Metadane i źródła wydania przygotowane: "+name)
