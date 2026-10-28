"""Load the separately verified NeoForge companion into the pack's real-client CI run."""
import hashlib
import json
from pathlib import Path
import sys
import urllib.request

mods = Path(sys.argv[1])
mods.mkdir(parents=True, exist_ok=True)
request = urllib.request.Request(
    'https://api.github.com/repos/stardely62-oss/modhyed-codex/releases/tags/neoforge-dev',
    headers={'User-Agent': 'mages-legacy-codex-integration-test'},
)
with urllib.request.urlopen(request, timeout=30) as response:
    release = json.load(response)
asset = next(a for a in release['assets'] if a['name'].startswith('ender-obelisks-')
             and a['name'].endswith('.jar') and not a['name'].endswith('-sources.jar'))
name = asset['name']
assert Path(name).name == name, 'Invalid companion asset name'
with urllib.request.urlopen(asset['browser_download_url'], timeout=60) as response:
    data = response.read()
digest = asset.get('digest')
if digest:
    assert digest == 'sha256:' + hashlib.sha256(data).hexdigest(), 'Companion checksum mismatch'
# Fail before replacing any fixture if the asset is not a native NeoForge jar.
import io
import zipfile
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    metadata = archive.read('META-INF/neoforge.mods.toml').decode()
    assert '21.1.' in metadata and '1.21.1' in metadata, 'Wrong companion platform'
    assert 'fabric.mod.json' not in archive.namelist(), 'Fabric companion is not supported'
for old in mods.glob('ender-obelisks-*.jar'):
    old.unlink()
(mods / name).write_bytes(data)
print('Integration companion:', name, 'sha256:', hashlib.sha256(data).hexdigest())
