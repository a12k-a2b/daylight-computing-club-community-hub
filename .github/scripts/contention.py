#!/usr/bin/env python3
"""Regenerate CONTENTION.md — the club's scent-mark board (HARMONY.md).

Stigmergy: cooks' AIs coordinate through what's written in the shared
environment. This script writes it: who holds which contended capability,
which are exclusive, and what's still free. Run by shelve.py after every
shelving; runnable by hand: python3 contention.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CORNERS = [f"overlay:corner-{c}" for c in ("tl", "tr", "bl", "br")]
# exclusive = only one dish can meaningfully hold it (HARMONY.md modes);
# everything else is shareable-if-it-yields
EXCLUSIVE = {"volume-keys", "notification-listener", "device-admin",
             "settings:brightness", *CORNERS}


def regenerate():
    catalog = json.loads((ROOT / "site" / "apps.json").read_text())
    holders = {}
    for a in catalog["apps"]:
        for u in a.get("uses", []):
            holders.setdefault(u, []).append(a["name"])

    lines = [
        "# Contention board — who holds what",
        "",
        "Auto-generated from `site/apps.json` by the shelving robot — do not",
        "edit by hand. The rules live in HARMONY.md: **first declarer keeps",
        "the capability; the newcomer adapts.** Cooks (and cooks' AIs): read",
        "this before choosing a key, corner, or setting.",
        "",
        "| capability | held by | mode |",
        "|---|---|---|",
    ]
    for cap in sorted(holders):
        mode = "exclusive" if cap in EXCLUSIVE else "shareable"
        lines.append(f"| `{cap}` | {', '.join(holders[cap])} | {mode} |")
    free = [c for c in CORNERS if c not in holders]
    lines += ["",
              "**Free corners:** " + (", ".join(f"`{c}`" for c in free) if free else "none — negotiate"),
              ""]
    (ROOT / "CONTENTION.md").write_text("\n".join(lines))
    return holders


if __name__ == "__main__":
    regenerate()
    print("CONTENTION.md regenerated")
