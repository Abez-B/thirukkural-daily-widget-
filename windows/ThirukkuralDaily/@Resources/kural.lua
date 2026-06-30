-- ═══════════════════════════════════════════════════════════
--  kural.lua  —  Thirukkural Daily Widget
--  Pure Lua. No Python. No internet. Native UTF-8 to UTF-16.
--  Author: Bharath · MIT License
-- ═══════════════════════════════════════════════════════════

local skinPath = ''
local kurals   = {}
local order    = {}
local epoch    = ''

-- ─────────────────────────────────────────────────────────
--  File helpers
-- ─────────────────────────────────────────────────────────

local function readLines(path)
    local t, f = {}, io.open(path, 'r')
    if not f then return t end
    for line in f:lines() do table.insert(t, line) end
    f:close()
    return t
end

local function readOne(path)
    return readLines(path)[1]
end

-- ─────────────────────────────────────────────────────────
--  UTF-8 to UTF-16 LE Converter
--  (Rainmeter strictly requires UTF-16 LE for Unicode files)
-- ─────────────────────────────────────────────────────────

local function utf8_to_utf16le(utf8_str)
    local utf16 = {}
    table.insert(utf16, string.char(0xFF, 0xFE)) -- BOM

    local i = 1
    while i <= #utf8_str do
        local b1 = string.byte(utf8_str, i)
        local cp = 0
        if not b1 then break end
        
        if b1 < 0x80 then
            cp = b1
            i = i + 1
        elseif b1 < 0xE0 then
            local b2 = string.byte(utf8_str, i+1) or 0
            cp = (b1 % 0x20) * 0x40 + (b2 % 0x40)
            i = i + 2
        elseif b1 < 0xF0 then
            local b2 = string.byte(utf8_str, i+1) or 0
            local b3 = string.byte(utf8_str, i+2) or 0
            cp = (b1 % 0x10) * 0x1000 + (b2 % 0x40) * 0x40 + (b3 % 0x40)
            i = i + 3
        else
            local b2 = string.byte(utf8_str, i+1) or 0
            local b3 = string.byte(utf8_str, i+2) or 0
            local b4 = string.byte(utf8_str, i+3) or 0
            cp = (b1 % 0x08) * 0x40000 + (b2 % 0x40) * 0x1000 + (b3 % 0x40) * 0x40 + (b4 % 0x40)
            i = i + 4
        end
        
        if cp >= 0x10000 then
            cp = cp - 0x10000
            local high = math.floor(cp / 0x400) + 0xD800
            local low = (cp % 0x400) + 0xDC00
            table.insert(utf16, string.char(high % 256, math.floor(high / 256)))
            table.insert(utf16, string.char(low % 256, math.floor(low / 256)))
        else
            table.insert(utf16, string.char(cp % 256, math.floor(cp / 256)))
        end
    end
    return table.concat(utf16)
end

-- ─────────────────────────────────────────────────────────
--  Load data
-- ─────────────────────────────────────────────────────────

local function loadKurals()
    local f = io.open(skinPath .. 'kurals.csv', 'r')
    if not f then return end
    for line in f:lines() do
        local id, paal, adhig, l1, l2, urai = line:match('^(%d+)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|(.-)$')
        if id then
            kurals[tonumber(id)] = {
                paal  = paal,
                line1 = l1,
                line2 = l2,
                urai  = urai
            }
        end
    end
    f:close()
end

local function loadCycle()
    for _, l in ipairs(readLines(skinPath .. 'cycle_order.txt')) do
        local n = tonumber(l)
        if n then table.insert(order, n) end
    end
    epoch = (readOne(skinPath .. 'cycle_epoch.txt') or '2023-12-03'):gsub('%s', '')
end

local function getDailyId()
    local ey, em, ed = epoch:match('(%d+)-(%d+)-(%d+)')
    if not ey then return order[1] or 1 end
    local eTime = os.time({ year=tonumber(ey), month=tonumber(em), day=tonumber(ed), hour=0, min=0, sec=0 })
    local now   = os.date('*t')
    local tTime = os.time({ year=now.year, month=now.month, day=now.day, hour=0, min=0, sec=0 })
    local days = math.floor((tTime - eTime) / 86400)
    local idx  = (days % #order) + 1
    return order[idx]
end

-- ─────────────────────────────────────────────────────────
--  Write variables.inc and Refresh
-- ─────────────────────────────────────────────────────────

local function applyKural(id)
    local k = kurals[id]
    if not k then return end

    local content = "[Variables]\n" ..
                    "KuralNum=" .. tostring(id) .. "\n" ..
                    "Paal=" .. k.paal .. "\n" ..
                    "Line1=" .. k.line1 .. "\n" ..
                    "Line2=" .. k.line2 .. "\n" ..
                    "Urai=" .. k.urai .. "\n"

    local utf16le = utf8_to_utf16le(content)
    
    local f = io.open(skinPath .. 'variables.inc', 'wb')
    if f then
        f:write(utf16le)
        f:close()
    end
    
    -- Refresh skin to load the new variables.inc
    SKIN:Bang('!Refresh')
end

-- ─────────────────────────────────────────────────────────
--  Public API
-- ─────────────────────────────────────────────────────────

function Initialize()
    skinPath = SKIN:GetVariable('@')
    
    local currentId = tonumber(SKIN:GetVariable('KuralNum'))
    
    loadCycle()
    local todayId = getDailyId()
    
    -- Only write & refresh if the currently loaded Kural is out of date
    if currentId ~= todayId then
        loadKurals()
        applyKural(todayId)
    end
end

function Update()
    return 1
end

-- Triggered by midnight measure
function Daily()
    loadCycle()
    local todayId = getDailyId()
    loadKurals()
    applyKural(todayId)
end
