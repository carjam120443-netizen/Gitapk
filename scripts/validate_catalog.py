#!/usr/bin/env python3
"""Validate GitAPK catalog manifests."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPS = ROOT / "catalog" / "apps"
CATALOG = ROOT / "catalog" / "catalog.json"
REQUIRED = {"id", "name", "summary", "category", "version", "apk", "sha256"}

manifests = []
for path in sorted(APPS.glob("*.json")):
    data = json.loads(path.read_text(encoding="utf-8"))
    missing = REQUIRED - data.keys()
    if missing:
        raise SystemExit(f"{path}: missing fields: {', '.join(sorted(missing))}")
    manifests.append(data)

ids = [app["id"] for app in manifests]
if len(ids) != len(set(ids)):
    raise SystemExit("Duplicate app IDs found")

catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
if catalog.get("schemaVersion") != 1:
    raise SystemExit("Unsupported catalog schemaVersion")

catalog_ids = {app["id"] for app in catalog.get("apps", [])}
manifest_ids = set(ids)
if catalog_ids != manifest_ids:
    raise SystemExit("catalog.json does not match catalog/apps manifests")

print(f"Catalog valid: {len(manifests)} app(s)")
