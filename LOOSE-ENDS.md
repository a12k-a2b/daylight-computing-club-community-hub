# Loose ends — open questions the club hasn't settled

Things deliberately left unresolved. Nothing here blocks anything; each is
a decision waiting for the right moment (or the right mood). Linked from
ROADMAP.md so it doesn't get lost.

---

## 1. The name: Computer vs. Computing

**Current state:** the club is named **Daylight Computer Club** everywhere
(site, poster, manifest, robots). Anjan has gone back and forth, and the
"Computing" option stays open. Both phrasings are recognized by Claude
sessions either way, and flipping is a five-minute sweep — the poster
re-renders from source. If we flip back, this section holds the reasoning
so it doesn't have to be re-derived.

**The case for *Daylight Computing Club*:**
- "Computing" is a verb wearing a noun's clothes — it names an *activity*,
  a practice, a way of being with machines. A computing club is a club
  where people compute together; a computer club risks sounding like a
  club about a gadget.
- It frames the bigger ambition: if this becomes the future of sharing
  bespoke personal software made for each other, that's a *philosophy of
  computing*, not an accessory community for one tablet. "Daylight
  Computing" names the philosophy.
- It's an *homage* to Homebrew rather than a find-and-replace of it — a
  name of our own.
- **The independence argument (the subtle one):** the company is Daylight
  Computer Co, so "Daylight Computer Club" reads like an official company
  program. "Computing" keeps a sliver of independence — this is the
  *members'* thing, not the manufacturer's. If the club ever needs to
  outlive, criticize, or simply stand apart from the company, that sliver
  matters.

**The case for *Daylight Computer Club* (the current name):**
- **The name is the address:** daylightcomputer.club. Say the name aloud
  and you've told someone the URL. For a club that spreads by texted link,
  that's infrastructure, not a pun.
- **The lineage lands without a footnote:** Homebrew Computer Club →
  Daylight Computer Club is legible in one beat. That rhyme is the
  founding story, and every new member gets it for free.
- **Register:** "computing club" carries a whiff of the university
  computing society; "computer club" sounds like a garage. This is a
  garage thing. Homebrew chose the humble object-y name and *became* a
  movement — clubs earn their abstractions by being concrete first.

**A middle path, if the back-and-forth continues:** keep *Daylight
Computer Club* as the club's proper name, and let "*daylight computing*"
be the adjective for the practice — tagline and manifesto language ("this
is what daylight computing looks like"). The address and the rhyme stay;
the philosophy keeps its name too.

**If we flip:** ask any Claude session to "rename the club back to
Daylight Computing Club" — sweep the site/poster/docs/robots, keep both
phrasings recognized, and consider whether daylightcomputer.club remains
the address (it can; names and addresses needn't match exactly).

## 2. The second app: is there a separate brightness APK?

Anjan's very first request mentioned a "daylight brightness app" alongside
Daylight Keys. What we found: `dc1-keys` (shelved — its brightness mode,
snip tool, and dog-ear cover the described features) and `dc1-backlight`
(engineering analysis docs, not an app). **Open question:** does a
separate brightness APK from another Claude session exist in some other
repo? If yes, name the repo and it goes on the shelf in minutes.

## 3. The repo's own name

The GitHub repo is still `daylight-computing-club-community-hub` ("computing",
and a mouthful). Renaming to match the club needs the owner's Settings
page; GitHub redirects old repo links automatically, and Railway follows
the rename. After any rename, ask a Claude session to sweep the repo-URL
references (share page, README, catalog source links). Low urgency — the
public face is the domain, not the repo.

## 4. The antivirus gate is built but unarmed

`shelve.py` blocks malicious APKs when VirusTotal flags them — but only
once a `VT_API_KEY` repo secret exists. Until then the inspector's report
says "🟡 antivirus scan: skipped." Worth arming before strangers find the
share form. Only the owner can do this (it needs a personal VirusTotal
account and repo-secret access); the two-minute recipe:

1. Sign up free at **virustotal.com** → click your avatar → **API key** → copy it.
2. GitHub repo → **Settings → Secrets and variables → Actions → New
   repository secret** → name `VT_API_KEY`, paste, save.

Nothing else to change — the code checks for the secret and arms itself.

## 5. Which Claude model runs each robot (cost vs. judgment)

The club's Claude-powered robots don't all need the same brains, and the
model is a one-line change in each workflow's `claude_args` (`--model ...`).
Current, deliberate choices — the principle is *dumb gates, smart advice,
cheap where judgment isn't needed*:

- **Concierge** (`club-concierge.yml`) → **Haiku** (`claude-haiku-4-5`).
  Warm, simple back-and-forth at the door; cheapest, plenty smart for it.
- **Newsletter** (`newsletter.yml`) → **Haiku**. Summarizing the month's
  history is easy; runs monthly, so cost barely matters either way.
- **Source reviewer** (`claude-review.yml`) → **Sonnet** (`claude-sonnet-5`).
  This one reads code for claims-vs-reality mismatches and hidden malice —
  real judgment. Sonnet is the sweet spot; Opus is overkill for a friends'
  club but a reasonable upgrade if the club ever admits strangers.

The **hard gates** (signing, VirusTotal, crash test) use no LLM at all —
by design, a gate must not be sweet-talkable (prompt injection). Only the
*advisory* reviewer and concierge use a model.

## 6. Sol:OS integration

The biggest unlock on the board — preinstall/bless the club, trust the
club's signing key as a "friends ring," or ship a privileged one-tap
installer. Fully written up in ROADMAP.md ("The Sol:OS card"). Needs a
conversation inside Daylight, not code.
