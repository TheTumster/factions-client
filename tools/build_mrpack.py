#!/usr/bin/env python3
"""
Builds "Factions Client.mrpack": one file your friends import into the Modrinth App
(or Prism Launcher) to get Fabric + Litematica + Printer + Sodium + this mod, preconfigured.

Usage: python tools/build_mrpack.py <path-to-mod-jar> <output.mrpack>
Optional env vars (GitHub secrets) to bake the backend config in:
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
    jar, out = sys.argv[1], sys.argv[2]

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

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(jar, "overrides/mods/" + os.path.basename(jar))
        url, key, team = (os.environ.get(k, "") for k in ("FC_SUPABASE_URL", "FC_SUPABASE_ANON_KEY", "FC_TEAM_KEY"))
        if url and key and team:
            cfg = {"supabaseUrl": url, "supabaseAnonKey": key, "teamKey": team}
            z.writestr("overrides/config/factionsclient.json", json.dumps(cfg, indent=2))
            print("  + backend config baked in")
        else:
            print("  ! no backend secrets set: players must fill config/factionsclient.json themselves")
    print(f"Wrote {out} (Fabric Loader {loader})")


if __name__ == "__main__":
    main()
