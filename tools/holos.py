"""CI helper for the hub preview: turn lag-network's hub holograms/displays (MiniMessage) into summonable components."""
import json, re, sys, yaml

NAMED = {"black": "#000000", "dark_blue": "#0000AA", "dark_green": "#00AA00", "dark_aqua": "#00AAAA", "dark_red": "#AA0000",
         "dark_purple": "#AA00AA", "gold": "#FFAA00", "gray": "#AAAAAA", "dark_gray": "#555555", "blue": "#5555FF",
         "green": "#55FF55", "aqua": "#55FFFF", "red": "#FF5555", "light_purple": "#FF55FF", "yellow": "#FFFF55", "white": "#FFFFFF"}
FILL = {"logo": "<white><bold>RELAPSED MC</bold>", "online": "128", "phase": "0", "map": "RELAPSE #1", "reset": "Launching December",
        "parkour_top": ""}


def lerp(a, b, t):
    a, b = int(a[1:], 16), int(b[1:], 16)
    return "#%02X%02X%02X" % tuple(round(((a >> s) & 255) * (1 - t) + ((b >> s) & 255) * t) for s in (16, 8, 0))


def mm(text):
    text = re.sub(r"\{(\w+)\}", lambda k: FILL.get(k.group(1), "12"), text)
    parts, stack = [], [{"color": "#FFFFFF"}]
    for tok in re.split(r"(<[^>]+>)", text):
        if not tok:
            continue
        if tok.startswith("<") and tok.endswith(">"):
            tag = tok[1:-1]
            if tag.startswith("/"):
                if len(stack) > 1:
                    stack.pop()
                continue
            st = dict(stack[-1])
            if tag in ("bold", "b"):
                st["bold"] = True
            elif tag in ("italic", "i"):
                st["italic"] = True
            elif tag in NAMED:
                st["color"] = NAMED[tag]
                st.pop("gradient", None)
            elif tag.startswith("#"):
                st["color"] = tag
                st.pop("gradient", None)
            elif tag.startswith("gradient"):
                st["gradient"] = [c for c in tag.split(":")[1:] if c.startswith("#")]
            stack.append(st)
            continue
        st = stack[-1]
        if st.get("gradient") and len(st["gradient"]) > 1:
            g = st["gradient"]
            n = max(1, len(tok) - 1)
            for i, ch in enumerate(tok):
                t = i / n * (len(g) - 1)
                k = min(int(t), len(g) - 2)
                parts.append({"text": ch, "color": lerp(g[k], g[k + 1], t - k), "bold": st.get("bold", False), "italic": st.get("italic", False)})
        else:
            parts.append({"text": tok, "color": st.get("gradient", [st["color"]])[0] if st.get("gradient") else st["color"],
                          "bold": st.get("bold", False), "italic": st.get("italic", False)})
    return [""] + parts


def argb(h):
    h = h.replace("#", "")
    v = int(h, 16)
    if len(h) <= 6:
        v |= 0xB0 << 24
    return v - (1 << 32) if v >= 1 << 31 else v


c = yaml.safe_load(open(sys.argv[1]))["hub"]
out = []
for h in c.get("holograms", []):
    o = {"offset": h["offset"], "scale": h.get("scale", 1.5), "snbt": json.dumps(mm(h["text"]), ensure_ascii=False),
         "billboard": h.get("billboard", "center"), "yaw": h.get("yaw", 0), "width": h.get("width", 400)}
    if "background" in h:
        o["background"] = argb(h["background"])
    out.append(o)
for d in c.get("displays", []):
    out.append({"offset": d["offset"], "scale": d.get("scale", 2), "item": d["item"]})
json.dump(out, open(sys.argv[2], "w"), indent=1, ensure_ascii=False)
print(len(out), "holos")
