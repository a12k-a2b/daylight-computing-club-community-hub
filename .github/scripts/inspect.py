#!/usr/bin/env python3
"""The club inspector — static checks on a submitted APK.

Reads what the app CAN DO from the APK itself (permissions, build flags)
and, when a VT_API_KEY secret is configured, asks VirusTotal's ~70
antivirus engines about the file. Returns a dict; also usable as a CLI:
    python3 inspect.py path/to/app.apk
"""
import hashlib, json, os, re, subprocess, sys, time, urllib.request

# Android permissions worth telling a friend about, in human words.
SENSITIVE = {
    "android.permission.CAMERA": "use the camera",
    "android.permission.RECORD_AUDIO": "listen through the microphone",
    "android.permission.ACCESS_FINE_LOCATION": "see your precise location",
    "android.permission.ACCESS_COARSE_LOCATION": "see your rough location",
    "android.permission.READ_CONTACTS": "read your contacts",
    "android.permission.WRITE_CONTACTS": "change your contacts",
    "android.permission.READ_CALENDAR": "read your calendar",
    "android.permission.WRITE_CALENDAR": "change your calendar",
    "android.permission.READ_SMS": "read your text messages",
    "android.permission.SEND_SMS": "send text messages",
    "android.permission.RECEIVE_SMS": "receive text messages",
    "android.permission.READ_CALL_LOG": "read your call history",
    "android.permission.READ_PHONE_STATE": "see your phone's identity",
    "android.permission.READ_EXTERNAL_STORAGE": "read your files",
    "android.permission.WRITE_EXTERNAL_STORAGE": "change your files",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "manage all your files",
    "android.permission.READ_MEDIA_IMAGES": "see your photos",
    "android.permission.SYSTEM_ALERT_WINDOW": "draw over other apps",
    "android.permission.WRITE_SETTINGS": "change system settings (like brightness)",
    "android.permission.POST_NOTIFICATIONS": "send notifications",
    "android.permission.REQUEST_INSTALL_PACKAGES": "install other apps",
    "android.permission.QUERY_ALL_PACKAGES": "see every app you have installed",
    "android.permission.INTERNET": "talk to the internet",
}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def virustotal(path):
    """Hash-lookup (and upload if unknown) against VirusTotal. None = skipped."""
    key = os.environ.get("VT_API_KEY")
    if not key:
        return None
    digest = sha256(path)
    api = "https://www.virustotal.com/api/v3"

    def get(url):
        req = urllib.request.Request(url, headers={"x-apikey": key})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise

    info = get(f"{api}/files/{digest}")
    if info is None:  # never seen — upload it and wait a little
        boundary = "clubinspector"
        with open(path, "rb") as f:
            payload = (f"--{boundary}\r\nContent-Disposition: form-data; "
                       f'name="file"; filename="app.apk"\r\n'
                       f"Content-Type: application/octet-stream\r\n\r\n").encode() \
                      + f.read() + f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(
            f"{api}/files", data=payload, method="POST",
            headers={"x-apikey": key,
                     "Content-Type": f"multipart/form-data; boundary={boundary}"})
        with urllib.request.urlopen(req, timeout=120) as r:
            analysis_id = json.load(r)["data"]["id"]
        for _ in range(12):  # up to ~2 minutes
            time.sleep(10)
            status = get(f"{api}/analyses/{analysis_id}")
            if status and status["data"]["attributes"]["status"] == "completed":
                break
        info = get(f"{api}/files/{digest}")
        if info is None:
            return {"pending": True, "link": f"https://www.virustotal.com/gui/file/{digest}"}

    stats = info["data"]["attributes"].get("last_analysis_stats", {})
    return {
        "malicious": stats.get("malicious", 0),
        "suspicious": stats.get("suspicious", 0),
        "engines": sum(stats.values()) or 0,
        "link": f"https://www.virustotal.com/gui/file/{digest}",
    }


def inspect(path):
    out = subprocess.run(["aapt", "dump", "badging", path],
                         capture_output=True, text=True).stdout
    perms = re.findall(r"uses-permission: name='([^']+)'", out)
    target = re.search(r"targetSdkVersion:'(\d+)'", out)
    report = {
        "package": (re.search(r"package: name='([^']+)'", out) or [None, ""])[1],
        "permissions": sorted(perms),
        "can": [SENSITIVE[p] for p in perms if p in SENSITIVE],
        "targetSdk": int(target.group(1)) if target else None,
        "debuggable": "application-debuggable" in out,
        "warnings": [],
    }
    if report["debuggable"]:
        report["warnings"].append("built in debug mode (fine for homebrew, but sloppy for sharing)")
    if report["targetSdk"] and report["targetSdk"] < 30:
        report["warnings"].append(f"targets old Android (SDK {report['targetSdk']}) — may behave oddly on the DC-1")
    BENIGN = {"android.permission.ACCESS_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE",
              "android.permission.VIBRATE", "android.permission.WAKE_LOCK",
              "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.FOREGROUND_SERVICE"}
    unknown = [p for p in perms if p.startswith("android.permission.")
               and p not in SENSITIVE and p not in BENIGN
               and not p.startswith("android.permission.FOREGROUND_SERVICE_")]
    if unknown:
        report["warnings"].append("asks for uncommon permissions: " + ", ".join(
            p.rsplit(".", 1)[-1] for p in unknown))

    try:
        report["virustotal"] = virustotal(path)
    except Exception as e:
        report["virustotal"] = {"error": str(e)[:200]}
    return report


def to_markdown(r):
    # traffic light per line: 🔴 caught something bad, 🟡 unsure/suspicious,
    # 🟢 checked and clean — so a keeper reads the verdict at a glance
    lines = ["**☀ Inspector's report**"]
    lines.append("- **This app can:** " + (", ".join(r["can"]) if r["can"]
                 else "nothing sensitive — asks for no notable permissions"))
    vt = r.get("virustotal")
    if vt is None:
        lines.append("- 🟡 **Antivirus scan:** skipped (no VirusTotal key configured)")
    elif vt.get("error"):
        lines.append(f"- 🟡 **Antivirus scan:** couldn't complete ({vt['error']})")
    elif vt.get("pending"):
        lines.append(f"- 🟡 **Antivirus scan:** still running — [check the result]({vt['link']})")
    elif vt.get("malicious", 0) > 0 or vt.get("suspicious", 0) > 0:
        lines.append(f"- 🔴 **Antivirus scan:** flagged by {vt['malicious']} of {vt['engines']} "
                     f"engines ({vt['suspicious']} more called it suspicious) — [details]({vt['link']})")
    else:
        lines.append(f"- 🟢 **Antivirus scan:** clean across {vt['engines']} engines ([details]({vt['link']}))")
    for w in r["warnings"]:
        lines.append(f"- 🟡 {w}")
    return "\n".join(lines)


if __name__ == "__main__":
    rep = inspect(sys.argv[1])
    print(json.dumps(rep, indent=2))
    print()
    print(to_markdown(rep))
