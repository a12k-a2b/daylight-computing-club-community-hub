#!/usr/bin/env python3
"""Shelve an app submitted through the share form (an `app-submission` issue).

Trusted members (.github/club-members.json) publish straight to the shelf;
everyone else's submission becomes a pull request for a keeper to approve.
Run by .github/workflows/shelve.yml. Set DRY_RUN=1 to test without git/gh.
"""
import json, os, re, subprocess, sys, tempfile, urllib.request
from datetime import date, timezone, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRY = os.environ.get("DRY_RUN") == "1"
ISSUE = os.environ.get("ISSUE_NUMBER", "0")
AUTHOR = os.environ.get("ISSUE_AUTHOR", "")
BODY = os.environ.get("ISSUE_BODY", "")
REPO = os.environ.get("REPO", "a12k-a2b/daylight-computing-club-community-hub")
SITE_URL = "https://daylightcomputer.club"


def sh(*cmd, **kw):
    print("+", " ".join(cmd), flush=True)
    return subprocess.run(cmd, check=True, text=True, capture_output=True, **kw).stdout


def comment(msg):
    print(f"[comment on #{ISSUE}]\n{msg}\n", flush=True)
    if not DRY:
        subprocess.run(["gh", "issue", "comment", ISSUE, "--repo", REPO, "--body", msg],
                       check=False, text=True)


def bail(msg):
    comment(f"Thanks for sharing! One thing before this can go on the shelf:\n\n{msg}\n\n"
            "Edit the issue (three dots menu → Edit) to fix it, then a keeper can "
            "re-run the shelving — or just reply here and a human will sort it out.")
    sys.exit(1)


def parse_form(body):
    """Issue forms render as '### <label>\\n\\n<value>' blocks."""
    fields = {}
    current = None
    for line in body.splitlines():
        if line.startswith("### "):
            current = line[4:].strip()
            fields[current] = []
        elif current is not None:
            fields[current].append(line)
    out = {}
    for label, lines in fields.items():
        value = "\n".join(lines).strip()
        out[label] = "" if value == "_No response_" else value
    return out


def field(fields, prefix):
    for label, value in fields.items():
        if label.lower().startswith(prefix.lower()):
            return value
    return ""


def slugify(name):
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug or "app"


def human_size(n):
    return f"{n/1024:.0f} KB" if n < 1024 * 1024 else f"{n/1024/1024:.1f} MB"


def download(url, dest):
    req = urllib.request.Request(url)
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token and "github" in url:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as r, open(dest, "wb") as f:
        f.write(r.read())


def prepare_apk(apk_field, app_id):
    m = re.search(r"\((https?://[^)\s]+)\)", apk_field) or re.search(r"(https?://\S+)", apk_field)
    if not m:
        bail("I couldn't find an attached file or link in the APK answer. "
             "Zip your `.apk` and drag the `.zip` into that answer, or paste a link to the file.")
    url = m.group(1)
    work = Path(tempfile.mkdtemp())
    raw = work / "download.bin"
    download(url, raw)

    apk = None
    if raw.read_bytes()[:2] == b"PK":  # zip or apk (both are zips)
        sh("unzip", "-o", "-q", str(raw), "-d", str(work / "x"))
        apks = list((work / "x").rglob("*.apk"))
        apk = apks[0] if apks else raw  # no .apk inside → maybe the file IS an apk
    if apk is None:
        bail("The attached file doesn't look like a zip or an APK. "
             "Zip your `.apk` (right-click → Compress) and attach that.")

    dest_dir = ROOT / "site" / "apps" / app_id
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / f"{app_id}.apk"
    aligned = work / "aligned.apk"
    sh("zipalign", "-f", "4", str(apk), str(aligned))
    sh("apksigner", "sign", "--ks", str(ROOT / "signing" / "dcc.keystore"),
       "--ks-pass", "pass:android", "--key-pass", "pass:android",
       "--out", str(dest), str(aligned))
    sh("apksigner", "verify", str(dest))
    Path(str(dest) + ".idsig").unlink(missing_ok=True)

    package = ""
    try:
        badging = sh("aapt", "dump", "badging", str(dest))
        pm = re.search(r"package: name='([^']+)'", badging)
        package = pm.group(1) if pm else ""
    except Exception:
        pass
    return {"file": f"apps/{app_id}/{app_id}.apk",
            "size": human_size(dest.stat().st_size),
            "package": package}


def main():
    fields = parse_form(BODY)
    name = field(fields, "App name")
    tagline = field(fields, "One-line tagline")
    author = field(fields, "Your name") or AUTHOR
    kind_raw = field(fields, "What kind of app")
    url = field(fields, "If it's a web app")
    apk_field = field(fields, "If it's an Android app")
    description = field(fields, "What does it do")
    setup = field(fields, "Any setup after installing")
    source = field(fields, "Where's the source code")

    if not name:
        bail("The **App name** answer is empty.")
    app_id = slugify(name)

    is_apk = "android" in kind_raw.lower() or (not url and apk_field)
    if not is_apk and not url:
        bail("It's marked as a web app, but the **link** answer is empty — paste the app's URL.")

    catalog_path = ROOT / "site" / "apps.json"
    catalog = json.loads(catalog_path.read_text())
    if any(a["id"] == app_id for a in catalog["apps"]):
        bail(f"There's already an app with the id `{app_id}` on the shelf. "
             "If this is an update, say so here and a keeper will handle it.")

    entry = {
        "id": app_id,
        "name": name,
        "tagline": tagline or name,
        "author": author,
        "type": "apk" if is_apk else "pwa",
        "version": "1.0",
        "updated": date.today().isoformat(),
        "description": description or tagline or name,
        "afterInstall": [s.strip() for s in setup.splitlines() if s.strip()],
        "source": source or f"https://github.com/{REPO}/issues/{ISSUE}",
    }
    if is_apk:
        entry["apk"] = prepare_apk(apk_field, app_id)
    else:
        entry["url"] = url

    catalog["apps"].insert(0, entry)
    catalog_path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(entry, indent=2))

    if DRY:
        print("[dry run — stopping before git]")
        return

    members = json.loads((ROOT / ".github" / "club-members.json").read_text())
    trusted = AUTHOR in members.get("trusted", [])

    sh("git", "config", "user.name", "club-robot")
    sh("git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    sh("git", "add", "-A")
    title = f"Shelve {name} (shared by @{AUTHOR})"

    if trusted:
        sh("git", "commit", "-m", f"{title}\n\nAuto-shelved: trusted club member. Closes #{ISSUE}")
        sh("git", "push", "origin", "HEAD:master")
        # pushes from the workflow token don't trigger the Pages deploy on their own
        subprocess.run(["gh", "workflow", "run", "pages.yml", "--ref", "master", "--repo", REPO],
                       check=False, text=True)
        comment(f"☀ **Shelved!** {name} is on the club shelf — live at {SITE_URL} "
                "in a couple of minutes. Thanks for bringing a dish.")
        subprocess.run(["gh", "issue", "close", ISSUE, "--repo", REPO,
                        "--reason", "completed"], check=False, text=True)
    else:
        # approve once, trusted forever: merging this PR also adds the
        # author to the trusted list, so their next share is instant
        members["trusted"].append(AUTHOR)
        members_path = ROOT / ".github" / "club-members.json"
        members_path.write_text(json.dumps(members, indent=2, ensure_ascii=False) + "\n")
        branch = f"shelve/issue-{ISSUE}"
        sh("git", "checkout", "-B", branch)
        sh("git", "add", "-A")
        sh("git", "commit", "-m", f"{title}\n\nCloses #{ISSUE}")
        sh("git", "push", "-f", "origin", branch)
        pr = sh("gh", "pr", "create", "--repo", REPO, "--base", "master", "--head", branch,
                "--title", title,
                "--body", f"Submitted through the share form by @{AUTHOR}. Closes #{ISSUE}.\n\n"
                          "A keeper reviews and merges — that's the approve tap. Merging also "
                          f"adds @{AUTHOR} to the trusted members list, so their future shares "
                          "publish instantly.")
        comment(f"Thanks @{AUTHOR}! Your app is prepped and waiting for a club keeper's "
                f"approval: {pr.strip()}. Once merged, it's on everyone's shelf — and your "
                "future shares will skip the wait entirely.")


if __name__ == "__main__":
    main()
