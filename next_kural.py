#!/usr/bin/env python3
import json
import datetime
from pathlib import Path

path = Path("/home/abe-z/Projects/kural_widget/kural_cycle.json")
with open(path, "r") as f:
    data = json.load(f)

# Subtract 1 day to simulate moving forward in time
current_epoch = datetime.date.fromisoformat(data["epoch_date"])
data["epoch_date"] = (current_epoch - datetime.timedelta(days=1)).isoformat()

import os

with open(path, "w") as f:
    json.dump(data, f, indent=2)

print(f"Moved to next Kural! New epoch: {data['epoch_date']}")
print("Reloading Eww widget...")
os.system("eww reload")
