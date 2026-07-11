#!/usr/bin/env python3
"""Generate a monstrous-but-valid Tracing Paper library to stress the app:
many notebooks, hundreds of pages, tens of thousands of strokes, snips
sprinkled through. Deterministic so runs are comparable."""
import json
import random
import sys

random.seed(7)


def stroke(kind=0):
    n = random.randint(4, 14)
    x, y = random.random(), random.random()
    pts = []
    for _ in range(n):
        x = min(max(x + random.uniform(-0.03, 0.03), 0), 1)
        y = min(max(y + random.uniform(-0.02, 0.02), 0), 1)
        pts += [round(x, 4), round(y, 4), round(random.uniform(0.2, 1.1), 2)]
    return {"k": kind, "w": 0.003, "p": pts}


def page(strokes, with_snip):
    p = {"s": [stroke(random.choice([0, 0, 0, 0, 2])) for _ in range(strokes)]}
    if with_snip:
        p["i"] = [{
            "f": f"seed-{random.randint(1, 8)}.png",
            "x": round(random.uniform(0, 0.5), 3),
            "y": round(random.uniform(0, 0.5), 3),
            "w": 0.4, "h": 0.3,
            "r": random.choice([0, 0, 0, -3.5, 12.0, 90.0]),
        }]
    return p


books = []
# a broad shelf of ordinary notebooks
for b in range(25):
    books.append({
        "n": f"Seed book {b + 1}",
        "t": b % 4,
        "c": 1700000000000,
        "m": 1700000000000,
        "pages": [page(60, i % 5 == 0) for i in range(12)],
    })
# and one monster the size of years of notes
books.append({
    "n": "The Monster",
    "t": 1,
    "c": 1700000000000,
    "m": 1700000000000,
    "pages": [page(150, i % 4 == 0) for i in range(80)],
})

doc = {"v": 2, "cur": 0, "books": books}
out = json.dumps(doc, separators=(",", ":"))
sys.stdout.write(out)
total_strokes = sum(len(p["s"]) for b in books for p in b["pages"])
total_pages = sum(len(b["pages"]) for b in books)
print(f"seeded: {len(books)} books, {total_pages} pages, {total_strokes} strokes, "
      f"{len(out)} bytes", file=sys.stderr)
