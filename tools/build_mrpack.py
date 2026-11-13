#!/usr/bin/env python3
"""
Builds "Factions Client.mrpack": one file your friends import into the Modrinth App
(or Prism Launcher) to get Fabric + Litematica + Printer + Sodium + this mod, preconfigured.

Usage: python tools/build_mrpack.py <mod-jar> <public.mrpack> <manifest.json> [<private.mrpack>]
The public pack + manifest go on the (public) GitHub release; the in-game updater reads the manifest.
If a private pack path is given and these env vars are set, that copy gets the backend config baked in:
  FC_SUPABASE_URL, FC_SUPABASE_ANON_KEY, FC_TEAM_KEY
"""
import json
import os
import sys
import urllib.parse
import urllib.request
import zipfile

MC = "1.21.11"
PROJECTS = ["fabric-api", "litematica", "malilib", "litematica-printer", "sodium",
            "xaeros-minimap", "xaeros-world-map"]
# Nice-to-haves: skipped (with a warning) if there's no 1.21.11 build
OPTIONAL = ["iris", "entityculling", "ferrite-core"]
# Modrinth asks for a User-Agent that identifies the project; generic ones get throttled.
UA = {"User-Agent": "TheTumster/factions-client/1.0 (github.com/TheTumster/factions-client)"}


def get(url):
    import time
    import urllib.error
    for attempt in range(8):
        req = urllib.request.Request(url, headers=UA)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429 and e.code < 500:
                raise
            wait = int(e.headers.get("X-Ratelimit-Reset") or e.headers.get("Retry-After") or 0) or 2 ** attempt
            print(f"  ({e.code} from {url.split('?')[0]}, retrying in {min(wait, 60)}s)")
            time.sleep(min(wait, 60))
    raise SystemExit(f"Gave up on {url}")


def latest_version(slug):
    q = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": f'["{MC}"]'})
    versions = get(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")
    if not versions:
        raise SystemExit(f"No {MC} Fabric build found for {slug}")
    releases = [v for v in versions if v["version_type"] == "release"]
    return (releases or versions)[0]


def main():
    jar, out, manifest_path = sys.argv[1], sys.argv[2], sys.argv[3]
    private_out = sys.argv[4] if len(sys.argv) > 4 else None

    picked = {}  # project_id -> version
    queue = [latest_version(s) for s in PROJECTS]
    for slug in OPTIONAL:
        try:
            queue.append(latest_version(slug))
        except SystemExit as e:
            print(f"  ! skipping optional {slug}: {e}")
    while queue:
        v = queue.pop()
        if v["project_id"] in picked:
            continue
        picked[v["project_id"]] = v
        for dep in v.get("dependencies", []):
            if dep["dependency_type"] != "required" or dep.get("project_id") in picked:
                continue
            if dep.get("version_id"):
                queue.append(get(f"https://api.modrinth.com/v2/version/{dep['version_id']}"))
            elif dep.get("project_id"):
                queue.append(latest_version(dep["project_id"]))

    files = []
    for v in picked.values():
        f = next((f for f in v["files"] if f["primary"]), v["files"][0])
        print(f"  + {f['filename']}")
        files.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })

    loaders = get(f"https://meta.fabricmc.net/v2/versions/loader/{MC}")
    loader = next(l["loader"]["version"] for l in loaders if l["loader"]["stable"])

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": os.environ.get("GITHUB_RUN_NUMBER", "dev"),
        "name": "Factions Client",
        "summary": "Private factions client",
        "files": files,
        "dependencies": {"minecraft": MC, "fabric-loader": loader},
    }

    def write_pack(path, cfg):
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("modrinth.index.json", json.dumps(index, indent=2))
            z.write(jar, "overrides/mods/" + os.path.basename(jar))
            if cfg:
                z.writestr("overrides/config/factionsclient.json", json.dumps(cfg, indent=2))

    write_pack(out, None)

    # Manifest for the in-game updater: every jar the pack manages, including this client.
    import hashlib
    jar_name = os.path.basename(jar)
    jar_sha1 = hashlib.sha1(open(jar, "rb").read()).hexdigest()
    mods = [{"filename": f["path"].split("/", 1)[1], "url": f["downloads"][0], "sha1": f["hashes"]["sha1"]} for f in files]
    mods.append({"filename": jar_name, "sha1": jar_sha1,
                 "url": f"https://github.com/TheTumster/factions-client/releases/download/latest/{jar_name}"})
    with open(manifest_path, "w") as m:
        json.dump({"build": index["versionId"], "minecraft": MC, "fabricLoader": loader, "mods": mods}, m, indent=2)
    print(f"Wrote {manifest_path} ({len(mods)} jars)")

    if private_out:
        url, key, team = (os.environ.get(k, "") for k in ("FC_SUPABASE_URL", "FC_SUPABASE_ANON_KEY", "FC_TEAM_KEY"))
        if url and key and team:
            write_pack(private_out, {"supabaseUrl": url, "supabaseAnonKey": key, "teamKey": team})
            print(f"Wrote {private_out} with backend config baked in (keep this one private)")
        else:
            print("  ! no backend secrets set: skipping private pack")
    print(f"Wrote {out} (Fabric Loader {loader})")


if __name__ == "__main__":
    main()
