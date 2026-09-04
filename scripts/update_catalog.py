#!/usr/bin/env python3
"""Build the GitAPK catalog from GitHub release APKs."""

import hashlib
import json
import os
import tempfile
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "catalog" / "sources.json"
APPS = ROOT / "catalog" / "apps"
CATALOG = ROOT / "catalog" / "catalog.json"
API = "https://api.github.com/repos/{}/releases/latest"


def get_json(url):
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "GitAPK-Catalog"})
    with urllib.request.urlopen(request, timeout=30) as response:
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


def main():
    sources = json.loads(SOURCES.read_text(encoding="utf-8"))["sources"]
    APPS.mkdir(parents=True, exist_ok=True)
    apps = []

    for source in sources:
        try:
            release = get_json(API.format(source["repository"]))
            assets = [a for a in release.get("assets", []) if a.get("name", "").lower().endswith(".apk")]
            if not assets:
                print(f"Skipping {source['name']}: latest release has no APK")
                continue

            # Prefer a universal APK when the project publishes one.
            assets.sort(key=lambda a: ("universal" not in a["name"].lower(), a["name"].lower()))
            asset = assets[0]
            apk_url = asset["browser_download_url"]
            sha256 = download_hash(apk_url)
            version = release.get("tag_name") or release.get("name") or "unknown"

            app = {
                "id": source["id"],
                "name": source["name"],
                "summary": source["summary"],
                "description": source["summary"],
                "category": source["category"],
                "license": source["license"],
                "version": version,
                "apk": apk_url,
                "sha256": sha256,
                "repository": "https://github.com/" + source["repository"],
                "release": release.get("html_url", "")
            }
            (APPS / f"{source['id']}.json").write_text(json.dumps(app, indent=2) + "\n", encoding="utf-8")
            apps.append({k: app[k] for k in ("id", "name", "summary", "category", "version", "apk", "sha256", "repository", "release")})
            print(f"Added {source['name']} {version}")
        except Exception as exc:
            print(f"Skipping {source['name']}: {exc}")

    catalog = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "apps": sorted(apps, key=lambda item: item["name"].lower())
    }
    CATALOG.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote catalog with {len(apps)} app(s)")


if __name__ == "__main__":
    main()
