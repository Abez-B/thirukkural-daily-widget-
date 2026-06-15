#!/usr/bin/env python3
import sqlite3
import json
import subprocess
from datetime import date
from pathlib import Path

def main():
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
    cursor.execute("SELECT Kural, Transliteration, Couplet, Vilakam, Kalaingar_Urai FROM kurals WHERE ID = ?", (kural_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        subprocess.run(["notify-send", "Thirukkural", "Kural not found."])
        return

    text = (f"<b>{row['Kural'].replace('<br />', '\\n')}</b>\n\n"
            f"<i>{row['Transliteration']}</i>\n\n"
            f"<b>Couplet:</b>\n{row['Couplet']}\n\n"
            f"<b>Vilakam:</b>\n{row['Vilakam']}\n\n"
            f"<b>Kalaingar Urai:</b>\n{row['Kalaingar_Urai']}")
            
    # Use zenity for a lightweight info dialog
    subprocess.run(["zenity", "--info", "--title=Thirukkural Daily", "--text=" + text, "--width=400"])

if __name__ == "__main__":
    main()
