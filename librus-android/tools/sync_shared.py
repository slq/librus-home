"""Copy/check the desktop adapter and snapshot logic, without account data."""
from pathlib import Path
import argparse

project = Path(__file__).resolve().parents[1]
desktop = project.parent / "librus-app"
parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
pairs = [(desktop / "librus_app" / name, project / "app/src/main/python/librus_shared" / name)
         for name in ("connector.py", "data.py")]
pairs += [(desktop / "LICENSE", project / "LICENSE"),
          (desktop / "licenses/librus-apix-GPL-3.0.txt", project / "licenses/librus-apix-GPL-3.0.txt")]
for source, target in pairs:
    if args.check:
        if not target.exists() or source.read_bytes() != target.read_bytes():
            raise SystemExit(f"Outdated shared source: {target.name}")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
print("Shared adapter, snapshots and licenses: OK")
