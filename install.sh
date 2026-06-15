#!/usr/bin/env bash

echo "📦 Installing Thirukkural Daily Widget..."

# Directories
EWW_DIR="$HOME/.config/eww"
DATA_DIR="$HOME/.local/share/kural_widget"
FONT_DIR="$HOME/.local/share/fonts"

# Create directories
mkdir -p "$EWW_DIR"
mkdir -p "$DATA_DIR"
mkdir -p "$FONT_DIR"

# Copy fonts
echo "🔡 Installing TAU-Kabilar Font..."
cp Kabilar.ttf "$FONT_DIR/"
fc-cache -f

# Copy Data Files
echo "💾 Copying Database and Scripts..."
cp thirukural.db "$DATA_DIR/"
cp kural_cycle.json "$DATA_DIR/"
cp kural_flashcard.py "$DATA_DIR/"
chmod +x "$DATA_DIR/kural_flashcard.py"

# Copy Eww Configs
echo "⚙️ Copying Eww Configurations..."
cp linux_eww/eww.yuck "$EWW_DIR/"
cp linux_eww/eww.scss "$EWW_DIR/"

# Update Absolute Paths in Eww Config
echo "🔗 Updating file paths..."
sed -i "s|/home/abe-z/Projects/kural_widget|$DATA_DIR|g" "$EWW_DIR/eww.yuck"
sed -i "s|/home/abe-z/Projects/kural_widget|$DATA_DIR|g" "$DATA_DIR/kural_flashcard.py"

echo "✅ Installation Complete!"
echo "Run 'eww daemon' and then 'eww open kural' to launch!"
