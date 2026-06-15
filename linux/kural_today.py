#!/usr/bin/env python3
import sqlite3
import json
import argparse
import sys
from datetime import date
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--format", choices=["waybar", "conky"], help="Output format")
    args = parser.parse_args()

    base_dir = Path(__file__).parent
    cycle_file = base_dir / "kural_cycle.json"
    db_file = base_dir / "thirukural.db"

    with open(cycle_file, "r") as f:
        cycle_data = json.load(f)
    
    epoch_date = date.fromisoformat(cycle_data["epoch_date"])
    cycle_length = cycle_data["cycle_length"]
    order = cycle_data["order"]

    today = date.today()
    days_since_epoch = (today - epoch_date).days
    day_index = ((days_since_epoch % cycle_length) + cycle_length) % cycle_length
    kural_id = order[day_index]

    conn = sqlite3.connect(db_file)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute("SELECT Kural, Couplet FROM kurals WHERE ID = ?", (kural_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        if args.format == "waybar":
            print(json.dumps({"text": "Error", "tooltip": "Kural not found"}))
        else:
            print("Kural not found.")
        sys.exit(1)
        
    kural_text = row["Kural"].replace("\n", " ").replace("<br />", " ")
    # Take the first few words for Waybar short text
    short_kural = " ".join(kural_text.split()[:4]) + "..."
    couplet = row["Couplet"]

    if args.format == "waybar":
        # Waybar output needs text, tooltip
        output = {
            "text": f"🪔 {short_kural}",
            "tooltip": f"{kural_text}\n\n{couplet}"
        }
        print(json.dumps(output))
    elif args.format == "conky":
        kural_text_conky = row["Kural"].replace("<br />", "\n").replace("\n\n", "\n")
        print(f"${{font Noto Sans Tamil:bold:size=14}}{kural_text_conky}${{font}}\n${{voffset 5}}${{color #aaaaaa}}{couplet}${{color}}")
    else:
        print(f"ID: {kural_id}")
        print(f"Kural: {kural_text}")
        print(f"Couplet: {couplet}")

if __name__ == "__main__":
    main()
