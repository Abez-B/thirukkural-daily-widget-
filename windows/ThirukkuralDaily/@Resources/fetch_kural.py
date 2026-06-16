import sqlite3
import json
import textwrap
import os
from datetime import date
import sys

# Script directory
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "thirukural.db")
CYCLE_PATH = os.path.join(BASE_DIR, "kural_cycle.json")
OUTPUT_PATH = os.path.join(BASE_DIR, "output.txt")

def get_kural_id(random_kural=False) -> int:
    with open(CYCLE_PATH, "r", encoding="utf-8") as f:
        cycle = json.load(f)

    if random_kural:
        import random
        return random.choice(cycle["order"])

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
    random_kural = "--random" in sys.argv
    kural_id = get_kural_id(random_kural)
    row = fetch_kural(kural_id)

    kural_text = row["Kural"].replace("<br />", "\n")
    kural_lines = [l.strip() for l in kural_text.strip().splitlines() if l.strip()]
    paal = row["Paal"].strip()
    urai_raw = (row["Kalaingar_Urai"] or "").strip()
    urai = textwrap.fill(urai_raw, width=60)
    
    k_line1 = kural_lines[0] if len(kural_lines) > 0 else ""
    k_line2 = kural_lines[1] if len(kural_lines) > 1 else ""

    output = f"{kural_id}|{paal}|{k_line1}|{k_line2}|{urai}"
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write(output)

if __name__ == "__main__":
    main()
