#!/usr/bin/env python3
"""The club caretaker — a weekly walk through the clubhouse.

Checks every pathway a dish can travel (the live site, the files on the
shelf, the Claude on-ramp) and posts a 🟢/🟡/🔴 report on the standing
"Caretaker log" issue. Dumb gate style: no LLM, just facts.
Run by .github/workflows/caretaker.yml (weekly cron + manual dispatch).
"""
import glob, json, os, re, subprocess, sys, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SITE = "https://daylightcomputer.club"
MIRROR = "https://a12k-a2b.github.io/daylight-computing-club-community-hub"
REPO = os.environ.get("REPO", "a12k-a2b/daylight-computing-club-community-hub")

lines, worst = [], "🟢"


def note(light, msg):
    global worst
    lines.append(f"- {light} {msg}")
    order = {"🟢": 0, "🟡": 1, "🔴": 2}
    if order[light] > order[worst]:
        worst = light


def head(url):
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, int(r.headers.get("Content-Length") or 0)
    except Exception:
        return 0, 0


# ---- pathway 1: the URL path (the site itself) ----
PAGES = ["", "install.html", "share.html", "guestbook.html", "wishboard.html",
         "newsletter.html", "why.html", "instincts.html", "invite.html",
         "apps.json", "recalled.json", "friends.json", "llms.txt",
         "sw.js", "manifest.webmanifest", "style.css"]
bad = [p for p in PAGES if head(f"{SITE}/{p}")[0] != 200]
if bad:
    note("🔴", f"site pages down on {SITE}: {', '.join(bad)}")
else:
    note("🟢", f"all {len(PAGES)} pages answer on {SITE}")
m_status, _ = head(f"{MIRROR}/")
note("🟢" if m_status == 200 else "🟡",
     f"GitHub Pages mirror {'answers' if m_status == 200 else 'is not answering (backup only, not urgent)'}")

# ---- pathway 2: the gift/dish path (catalog + files + signatures) ----
try:
    catalog = json.loads((ROOT / "site" / "apps.json").read_text())
    required = ["id", "name", "tagline", "author", "type", "version", "updated", "description", "source"]
    problems = []
    friends = json.loads((ROOT / "site" / "friends.json").read_text()).get("friends", {})
    apksigner = sorted(glob.glob(os.path.expandvars("$ANDROID_HOME/build-tools/*/apksigner"))) or ["apksigner"]
    for a in catalog["apps"]:
        missing = [k for k in required if not a.get(k)]
        if missing:
            problems.append(f"{a.get('id','?')} missing {missing}")
        for name in a.get("for", []):
            if name not in friends:
                problems.append(f"{a['id']} is dedicated to '{name}' who has no seat in friends.json")
        if a.get("type") == "apk":
            f = ROOT / "site" / a["apk"]["file"]
            if not f.exists():
                problems.append(f"{a['id']}: APK file missing from repo")
                continue
            v = subprocess.run([apksigner[-1], "verify", str(f)], capture_output=True, text=True)
            if v.returncode != 0:
                problems.append(f"{a['id']}: signature does NOT verify")
            status, size = head(f"{SITE}/{a['apk']['file']}")
            if status != 200 or size == 0:
                problems.append(f"{a['id']}: live APK link broken ({status})")
    if problems:
        note("🔴", "shelf problems: " + "; ".join(problems))
    else:
        n_apk = sum(1 for a in catalog["apps"] if a.get("type") == "apk")
        note("🟢", f"catalog valid, all {n_apk} APKs present, signed, and downloadable")
    json.loads((ROOT / "site" / "recalled.json").read_text())
    note("🟢", "recalled.json parses")
except Exception as e:
    note("🔴", f"catalog check crashed: {e}")

# ---- pathway 3: the Claude path ----
llms, _ = head(f"{SITE}/llms.txt")
form = (ROOT / ".github" / "ISSUE_TEMPLATE" / "share-an-app.yml").exists()
note("🟢" if llms == 200 and form else "🔴",
     "Claude on-ramp: llms.txt live and the share form template exists"
     if llms == 200 and form else "Claude on-ramp broken (llms.txt or share form missing)")

# ---- robots still parse ----
try:
    import yaml
    for wf in (ROOT / ".github" / "workflows").glob("*.yml"):
        yaml.safe_load(wf.read_text())
    note("🟢", "all robot workflows parse")
except Exception as e:
    note("🔴", f"a workflow file is broken: {e}")

report = (f"## {worst} Caretaker's weekly walk-through\n\n" + "\n".join(lines) +
          "\n\n*Every pathway a dish travels, checked. "
          "🔴 means guests are affected — fix today. 🟡 can wait for daylight.*")
print(report)

if os.environ.get("GH_TOKEN"):
    find = subprocess.run(["gh", "issue", "list", "--repo", REPO, "--state", "open",
                           "--search", "Caretaker log in:title", "--json", "number"],
                          capture_output=True, text=True)
    nums = json.loads(find.stdout or "[]")
    if nums:
        num = str(nums[0]["number"])
    else:
        made = subprocess.run(["gh", "issue", "create", "--repo", REPO,
                               "--title", "Caretaker log",
                               "--body", "The caretaker walks the clubhouse weekly and reports here."],
                              capture_output=True, text=True)
        num = made.stdout.strip().rsplit("/", 1)[-1]
    subprocess.run(["gh", "issue", "comment", num, "--repo", REPO, "--body", report],
                   check=False, text=True)

sys.exit(1 if worst == "🔴" else 0)
