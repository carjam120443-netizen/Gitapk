#!/usr/bin/env python3
"""Build the GitAPK catalog from GitHub releases and F-Droid repositories."""

import hashlib
import json
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "catalog" / "sources.json"
REPOSITORIES = ROOT / "catalog" / "repositories.json"
APPS = ROOT / "catalog" / "apps"
CATALOG = ROOT / "catalog" / "catalog.json"
GITHUB_API = "https://api.github.com/repos/{}/releases/latest"


def get_json(url):
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "GitAPK-Catalog"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def download_hash(url):
    request = urllib.request.Request(url, headers={"User-Agent": "GitAPK-Catalog"})
    digest = hashlib.sha256()
    with urllib.request.urlopen(request, timeout=120) as response:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def github_apps(sources):
    apps = []
    for source in sources:
        try:
            release = get_json(GITHUB_API.format(source["repository"]))
            assets = [a for a in release.get("assets", []) if a.get("name", "").lower().endswith(".apk")]
            if not assets:
                print(f"Skipping {source['name']}: latest release has no APK")
                continue
            assets.sort(key=lambda a: ("universal" not in a["name"].lower(), a["name"].lower()))
            asset = assets[0]
            apk_url = asset["browser_download_url"]
            app = {
                "id": source["id"], "name": source["name"], "summary": source["summary"],
                "description": source["summary"], "category": source["category"], "license": source["license"],
                "version": release.get("tag_name") or release.get("name") or "unknown",
                "apk": apk_url, "sha256": download_hash(apk_url),
                "repository": "https://github.com/" + source["repository"],
                "release": release.get("html_url", ""), "sourceType": "github"
            }
            (APPS / f"{source['id']}.json").write_text(json.dumps(app, indent=2) + "\n", encoding="utf-8")
            apps.append(app)
            print(f"Added {source['name']} {app['version']}")
        except Exception as exc:
            print(f"Skipping {source['name']}: {exc}")
    return apps


def fdroid_apps(repositories):
    apps = []
    for repo in repositories:
        if not repo.get("enabled", True) or repo.get("type") != "fdroid":
            continue
        try:
            index = get_json(repo["index"])
            packages = index.get("packages", {})
            imported = 0
            for package_id, metadata in packages.items():
                versions = metadata.get("versions", {})
                if not versions:
                    continue
                latest = max(versions.values(), key=lambda item: item.get("versionCode", 0))
                apk_name = latest.get("apkName")
                if not apk_name:
                    continue
                apk_url = repo["url"].rstrip("/") + "/" + apk_name
                app = {
                    "id": package_id,
                    "name": metadata.get("name") or package_id,
                    "summary": metadata.get("summary") or "F-Droid application",
                    "description": metadata.get("description") or metadata.get("summary") or "F-Droid application",
                    "category": (metadata.get("categories") or ["Other"])[0],
                    "license": metadata.get("license") or "Unknown",
                    "version": latest.get("versionName") or str(latest.get("versionCode", "unknown")),
                    "versionCode": latest.get("versionCode"),
                    "apk": apk_url,
                    "sha256": latest.get("hash", ""),
                    "repository": repo["url"],
                    "sourceType": "fdroid",
                    "sourceRepository": repo["id"]
                }
                filename = f"fdroid-{repo['id']}-{package_id}.json".replace("/", "-")
                (APPS / filename).write_text(json.dumps(app, indent=2) + "\n", encoding="utf-8")
                apps.append(app)
                imported += 1
            print(f"Imported {imported} package(s) from {repo['name']}")
        except Exception as exc:
            print(f"Skipping {repo['name']}: {exc}")
    return apps


def main():
    APPS.mkdir(parents=True, exist_ok=True)
    sources = json.loads(SOURCES.read_text(encoding="utf-8"))["sources"]
    repositories = json.loads(REPOSITORIES.read_text(encoding="utf-8"))["repositories"]
    apps = github_apps(sources)
    apps.extend(fdroid_apps(repositories))
    catalog = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "apps": sorted(apps, key=lambda item: (item["name"].lower(), item["id"]))
    }
    CATALOG.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote catalog with {len(apps)} app(s)")


if __name__ == "__main__":
    main()
