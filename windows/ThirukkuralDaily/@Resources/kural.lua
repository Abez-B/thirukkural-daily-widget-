-- ═══════════════════════════════════════════════════════════
--  kural.lua  —  Thirukkural Daily Widget
--  Pure Lua. No Python. No internet. Runs natively in Rainmeter.
--  Author: Bharath · MIT License
-- ═══════════════════════════════════════════════════════════

local skinPath = ''
local kurals   = {}
local order    = {}
local epoch    = ''

-- UTF-8 byte sequences for special characters (Lua decimal escapes)
local ARROW_LEFT = '\226\134\144'   -- ← (U+2190)
local BULLET     = '\194\183'       -- · (U+00B7)
local GUILLEMET  = '\226\128\186'   -- › (U+203A)

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

local function writeLines(path, t)
    local f = io.open(path, 'w')
    if not f then return end
    for _, l in ipairs(t) do f:write(l .. '\n') end
    f:close()
end

local function readOne(path)
    return readLines(path)[1]
end

local function writeOne(path, val)
    writeLines(path, {tostring(val)})
end

-- ─────────────────────────────────────────────────────────
--  Load kural data from kurals.csv
--  Format: ID|Paal|Adhigaram|Line1|Line2|Urai
-- ─────────────────────────────────────────────────────────

local function loadKurals()
    local f = io.open(skinPath .. 'kurals.csv', 'r')
    if not f then
        SKIN:Bang('!Log', 'kural.lua: kurals.csv not found at ' .. skinPath, 'Error')
        return
    end
    for line in f:lines() do
        local id, paal, adhig, l1, l2, urai =
            line:match('^(%d+)|([^|]*)|([^|]*)|([^|]*)|([^|]*)|(.-)$')
        if id then
            kurals[tonumber(id)] = {
                paal  = paal,
                adhig = adhig,
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

-- ─────────────────────────────────────────────────────────
--  Epoch math
-- ─────────────────────────────────────────────────────────

local function getDailyId()
    local ey, em, ed = epoch:match('(%d+)-(%d+)-(%d+)')
    if not ey then return order[1] or 1 end
    local eTime = os.time({
        year  = tonumber(ey), month = tonumber(em), day = tonumber(ed),
        hour  = 0, min = 0, sec = 0
    })
    local now   = os.date('*t')
    local tTime = os.time({
        year  = now.year, month = now.month, day = now.day,
        hour  = 0, min = 0, sec = 0
    })
    local days = math.floor((tTime - eTime) / 86400)
    local idx  = (days % #order) + 1
    return order[idx]
end

-- ─────────────────────────────────────────────────────────
--  History  (history.txt — one ID per line, newest last)
-- ─────────────────────────────────────────────────────────

local function loadHistory()
    local t = {}
    for _, l in ipairs(readLines(skinPath .. 'history.txt')) do
        local n = tonumber(l)
        if n then table.insert(t, n) end
    end
    return t
end

local function saveHistory(stack)
    local t, s = {}, math.max(1, #stack - 19)
    for i = s, #stack do table.insert(t, tostring(stack[i])) end
    writeLines(skinPath .. 'history.txt', t)
end

-- ─────────────────────────────────────────────────────────
--  Set Rainmeter variables and redraw
-- ─────────────────────────────────────────────────────────

local function display(id)
    local k = kurals[id]
    if not k then
        SKIN:Bang('!Log', 'kural.lua: kural id ' .. tostring(id) .. ' not found', 'Error')
        return
    end

    local stack = loadHistory()

    local breadcrumb = k.paal
    if k.adhig and k.adhig ~= '' then
        breadcrumb = k.paal .. '  ' .. GUILLEMET .. '  ' .. k.adhig
    end

    local prevBtn = (#stack > 0) and ARROW_LEFT or BULLET

    SKIN:Bang('!SetVariable', 'KuralNum',   tostring(id))
    SKIN:Bang('!SetVariable', 'Paal',       k.paal)
    SKIN:Bang('!SetVariable', 'Breadcrumb', breadcrumb)
    SKIN:Bang('!SetVariable', 'Line1',      k.line1)
    SKIN:Bang('!SetVariable', 'Line2',      k.line2)
    SKIN:Bang('!SetVariable', 'Urai',       k.urai)
    SKIN:Bang('!SetVariable', 'PrevBtn',    prevBtn)
    SKIN:Bang('!UpdateMeter', '*')
    SKIN:Bang('!Redraw')

    writeOne(skinPath .. 'current.txt', id)
end

-- ─────────────────────────────────────────────────────────
--  Public API  (called via !CommandMeasure MeasureKural "Fn")
-- ─────────────────────────────────────────────────────────

function Initialize()
    skinPath = SKIN:GetVariable('@')
    loadKurals()
    loadCycle()
    local saved = tonumber(readOne(skinPath .. 'current.txt') or '')
    display(saved or getDailyId())
end

function Update()
    return tonumber(readOne(skinPath .. 'current.txt') or '1') or 1
end

function Daily()
    writeLines(skinPath .. 'history.txt', {})
    display(getDailyId())
end

function Random()
    local current = tonumber(readOne(skinPath .. 'current.txt') or '') or getDailyId()
    local stack   = loadHistory()
    table.insert(stack, current)
    saveHistory(stack)
    local newId = current
    if #order > 1 then
        repeat newId = order[math.random(1, #order)] until newId ~= current
    end
    display(newId)
end

function Previous()
    local stack = loadHistory()
    if #stack > 0 then
        local prev = stack[#stack]
        table.remove(stack)
        saveHistory(stack)
        display(prev)
    else
        Daily()
    end
end
