#!/usr/bin/env python3
"""
Computes today's Thirukkural using the fixed 1330-day cycle.

Modes:
  (no flag)   - plain text flashcard, useful for debugging
  --json      - JSON output for eww's defpoll:
                {"id": int, "paal": str, "kural": "line1\nline2", "urai": str}
"""

import sqlite3
import json
import textwrap
import os
import sys
from datetime import date

# --- Paths: adjust if your project folder is elsewhere ---
BASE_DIR = os.path.expanduser("~/Projects/kural_widget")
DB_PATH = os.path.join(BASE_DIR, "thirukural.db")
CYCLE_PATH = os.path.join(BASE_DIR, "kural_cycle.json")

# Wrap width for the explanation text (used only in plain-text mode)
WRAP_WIDTH = 46


def get_today_kural_id() -> int:
    with open(CYCLE_PATH, "r", encoding="utf-8") as f:
        cycle = json.load(f)

    epoch = date.fromisoformat(cycle["epoch_date"])
    today = date.today()
    days_since_epoch = (today - epoch).days
    cycle_length = cycle["cycle_length"]

    day_index = ((days_since_epoch % cycle_length) + cycle_length) % cycle_length
    return cycle["order"][day_index]


def fetch_kural(kural_id: int) -> sqlite3.Row:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.execute("SELECT * FROM kurals WHERE ID = ?", (kural_id,))
    row = cur.fetchone()
    conn.close()
    if row is None:
        raise ValueError(f"No kural found with ID {kural_id}")
    return row


def main():
    kural_id = get_today_kural_id()
    row = fetch_kural(kural_id)

    # Replaced <br /> so splitlines() works correctly
    kural_text = row["Kural"].replace("<br />", "\n")
    kural_lines = [l.strip() for l in kural_text.strip().splitlines() if l.strip()]
    paal = row["Paal"].strip()
    urai_raw = (row["Kalaingar_Urai"] or "").strip()

    if "--json" in sys.argv:
        data = {
            "id": kural_id,
            "paal": paal,
            "kural_line1": kural_lines[0] if len(kural_lines) > 0 else "",
            "kural_line2": kural_lines[1] if len(kural_lines) > 1 else "",
            "urai": textwrap.fill(urai_raw, width=55),
        }
        print(json.dumps(data, ensure_ascii=False))
        return

    # Conky formatted mode (when --json is omitted)
    urai = textwrap.fill(urai_raw, width=42)
    k_line1 = kural_lines[0] if len(kural_lines) > 0 else ""
    k_line2 = kural_lines[1] if len(kural_lines) > 1 else ""
    
    output = (
        f"${{color #f9e2af}}குறள் {kural_id}${{color}}   ${{color #89b4fa}}—  {paal}${{color}}\\n\\n"
        f"${{color #cdd6f4}}${{font TAU-Kabilar:bold:size=16}}{k_line1}\\n{k_line2}${{font}}${{color}}\\n\\n"
        f"${{color #a6adc8}}${{font TAU-Kabilar:bold:size=12}}கலைஞர் உரை:${{font}}${{color}}\\n"
        f"${{color #bac2de}}{urai}${{color}}"
    )
    print(output)


if __name__ == "__main__":
    main()
