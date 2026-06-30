import sqlite3
import json
import os
import sys
import random
from datetime import date

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "thirukural.db")
CYCLE_PATH = os.path.join(BASE_DIR, "kural_cycle.json")
OUTPUT_PATH = os.path.join(BASE_DIR, "variables.inc")
HISTORY_PATH = os.path.join(BASE_DIR, "history.json")

def get_daily_kural_id() -> int:
    with open(CYCLE_PATH, "r", encoding="utf-8") as f:
        cycle = json.load(f)
    epoch = date.fromisoformat(cycle["epoch_date"])
    today = date.today()
    days_since_epoch = (today - epoch).days
    cycle_length = cycle["cycle_length"]
    day_index = ((days_since_epoch % cycle_length) + cycle_length) % cycle_length
    return cycle["order"][day_index]

def load_history():
    if not os.path.exists(HISTORY_PATH):
        return {"stack": [], "current": None}
    with open(HISTORY_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

def save_history(data):
    with open(HISTORY_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f)

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

def write_variables(row, current_id, has_history):
    kural_text = row["Kural"].replace("<br />", "\n")
    kural_lines = [l.strip() for l in kural_text.strip().splitlines() if l.strip()]
    line1 = kural_lines[0] if len(kural_lines) > 0 else ""
    line2 = kural_lines[1] if len(kural_lines) > 1 else ""

    paal = (row["Paal"] or "").strip()
    adhigaram = (row["Adhigaram"] or "").strip()
    breadcrumb = f"{paal}  ›  {adhigaram}" if adhigaram else paal

    urai_raw = (row["Kalaingar_Urai"] or "").strip()
    # Keep urai as-is (Rainmeter handles wrapping via W=)
    # Truncate very long urai with ellipsis if needed
    if len(urai_raw) > 200:
        urai_raw = urai_raw[:197] + "..."

    english = (row.get("Couplet") or row.get("Vilakam") or "").strip() if hasattr(row, "get") else ""
    try:
        english = (row["Couplet"] or "").strip()
    except Exception:
        english = ""

    prev_btn = "←" if has_history else "·"

    with open(OUTPUT_PATH, "w", encoding="utf-16-le") as f:
        f.write("\ufeff")  # BOM
        f.write("[Variables]\n")
        f.write(f"KuralID=குறள் {current_id}\n")
        f.write(f"Breadcrumb={breadcrumb}\n")
        f.write(f"Line1={line1}\n")
        f.write(f"Line2={line2}\n")
        f.write(f"UraiTitle=கலைஞர் உரை\n")
        f.write(f'Urai={urai_raw}\n')
        f.write(f"PrevBtn={prev_btn}\n")
        f.write(f"RandomBtn=⟳\n")
        f.write(f"ShareBtn=↗\n")

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "daily"
    history = load_history()

    if mode == "--random":
        # Push current to history, pick random
        with open(CYCLE_PATH, "r", encoding="utf-8") as f:
            cycle = json.load(f)
        if history["current"] is not None:
            history["stack"].append(history["current"])
            # Cap history at 20
            if len(history["stack"]) > 20:
                history["stack"] = history["stack"][-20:]
        new_id = random.choice(cycle["order"])
        history["current"] = new_id
        save_history(history)
        kural_id = new_id

    elif mode == "--prev":
        # Pop from history
        if history["stack"]:
            kural_id = history["stack"].pop()
            history["current"] = kural_id
            save_history(history)
        else:
            # No history — fall back to today
            kural_id = get_daily_kural_id()
            history["current"] = kural_id
            save_history(history)

    elif mode == "--daily":
        # Reset to today's kural, clear history
        kural_id = get_daily_kural_id()
        history = {"stack": [], "current": kural_id}
        save_history(history)

    else:
        # Default: load today's kural if no current saved
        if history["current"] is None:
            kural_id = get_daily_kural_id()
            history["current"] = kural_id
            save_history(history)
        else:
            kural_id = history["current"]

    row = fetch_kural(kural_id)
    write_variables(row, kural_id, has_history=len(history["stack"]) > 0)

if __name__ == "__main__":
    main()
