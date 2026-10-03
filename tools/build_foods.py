#!/usr/bin/env python3
"""Build app/src/main/assets/foods.tsv.gz, Wiggle's bundled offline food database.

Run:  python tools/build_foods.py      (stdlib only; downloads go to tools/.cache/, reused on re-runs)

Sources (all nutrient values come from these files unchanged, only rounded):

US  USDA FoodData Central: SR Legacy (2018-04), Foundation Foods (2025-12-18), FNDDS survey foods (2024-10-31)
    https://fdc.nal.usda.gov/download-datasets/
    Licence: public domain, published under CC0 1.0 Universal. "No permission is needed for their use,
    but we request that users list FoodData Central as the source of the data." (fdc.nal.usda.gov)
    Household portions come from each dataset's food_portion.csv.

UK  McCance and Widdowson's Composition of Foods Integrated Dataset (CoFID) 2021, Public Health England
    https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid
    Licence: Crown copyright, Open Government Licence v3.0 (gov.uk: "All content is available under the
    Open Government Licence v3.0, except where otherwise stated"; nothing else stated for CoFID).
    Required attribution: "Contains public sector information licensed under the Open Government Licence v3.0."
    Has many cooked South Asian dishes (dals, bhajis, curries, pakoras). No portions, so serving is per 100 g.

IN  Indian Nutrient Databank (INDB), recipe table INDB.xlsx, pinned to commit fbdb62b
    https://github.com/lindsayjaacks/Indian-Nutrient-Databank-INDB-
    Paper: Vijayakumar A, Dubasi HB, Awasthi A, Jaacks LM. Development of an Indian Food Composition Database.
    Curr Dev Nutr 2024;8:103790, doi:10.1016/j.cdnut.2024.103790 (article licensed CC BY 4.0).
    Licence: the repo has NO licence file. The paper calls INDB "a unique open-access resource [that] can be
    used by researchers, the government, and the private and third sectors" and says "All analysis codes and
    files are publicly and freely available on GitHub". Treat as attribution-required; get written permission
    from the authors (aswathy@anuvaad.org.in) before a store release, or set INCLUDE_INDB = False.
    Values are per 100 g of *raw ingredients* (no cooking yield), so deep-fried recipes count all the frying
    oil; those rows (fat > 40 g/100 g) and rows whose energy disagrees with their macros are dropped, as are
    rows without a serving (weaning foods, pickles/preserves). Per-serving totals are the trustworthy figure;
    per-100 g runs high for dishes made from dry grain or dal (plain dosa 381 kcal/100 g, 137 kcal per dosa).
    The raw IFCT 2017 (ICMR-NIN) table is NOT used: it must be requested from NIN and is not redistributable.

Attribution line for the app:
    Food data: USDA FoodData Central (CC0); UK CoFID 2021, contains public sector information licensed under
    the Open Government Licence v3.0; Indian Nutrient Databank (Vijayakumar et al. 2024).

Output: gzip of UTF-8 TSV, header `name aka kcal protein carbs fat fiber serving serving_g source`,
values per 100 g (per 100 ml for drinks). Note "carbs" is total carbohydrate for US (includes fibre) and
available carbohydrate for UK/IN (excludes fibre) -- that is how each source reports it.
"""
import csv
import gzip
import io
import re
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".cache"
OUT = ROOT / "app" / "src" / "main" / "assets" / "foods.tsv.gz"
HEADER = ["name", "aka", "kcal", "protein", "carbs", "fat", "fiber", "serving", "serving_g", "source"]

FDC = "https://fdc.nal.usda.gov/fdc-datasets/"
FNDDS = "FoodData_Central_survey_food_csv_2024-10-31.zip"
FOUNDATION = "FoodData_Central_foundation_food_csv_2025-12-18.zip"
SR = "FoodData_Central_sr_legacy_food_csv_2018-04.zip"
COFID_URL = ("https://assets.publishing.service.gov.uk/media/60538b91e90e07527df82ae4/"
             "McCance_Widdowsons_Composition_of_Foods_Integrated_Dataset_2021..xlsx")
INDB_URL = ("https://raw.githubusercontent.com/lindsayjaacks/Indian-Nutrient-Databank-INDB-/"
            "fbdb62bec519c6e2468582a799fb47e194b42558/INDB.xlsx")
INCLUDE_INDB = True

# Rows nobody logs as a meal: infant food, lab/industrial ingredients, recipe sub-components, as-purchased weights.
JUNK = re.compile(r"infant|babyfood|baby food|toddler|\bformula\b|\bhuman\b|industrial|school lunch|as ingredient"
                  r"|for use (with|on)|weighed with|0% moisture", re.I)
BRAND = re.compile(r"\b(?!BBQ\b|NFS\b|USDA)[A-Z][A-Z'&.]{2,}")  # SR/FNDDS write brands in caps: KELLOGG'S, McDONALD'S

# Extra search words. One group per line; a food whose name contains any phrase gets the other words as aka.
SYNONYMS = """\
chapati|chapatti|chappatti|roti|phulka
paratha|parantha|parotta
puri|poori
dal|dahl|dhal|daal|lentil
toor|tur|tuvar|arhar|pigeon pea
moong|mung|green gram
urad|urd|black gram
masoor|red lentil
chana|channa|chole|chickpea|chick pea|garbanzo|bengal gram
rajma|rajmah|kidney bean
lobia|chawli|cowpea|blackeye|black-eye|black-eyed
besan|gram flour|chickpea flour
maida|all-purpose
atta|whole wheat flour|whole-wheat flour|wholemeal flour|chapati flour
sooji|suji|rava|semolina
poha|flattened rice|rice flakes|beaten rice|chivda|chiwda|aval
murmura|puffed rice|kurmura
sabudana|sago|tapioca pearl
seviyan|semiya|vermicelli
dahi|curd|yogurt|yoghurt
chaas|chhaas|buttermilk
ghee|clarified butter
khoa|khoya|mawa
brinjal|baingan|eggplant|aubergine
bhindi|okra|lady finger|ladies finger
lauki|ghiya|dudhi|bottle gourd|calabash
karela|bitter gourd|bitter melon|balsam pear
tinda|round gourd
turai|ridge gourd
aloo|potato
gobi|gobhi|cauliflower
pattagobhi|cabbage
palak|spinach
methi|fenugreek
matar|mutter|green peas|peas, green|garden peas
pyaz|pyaaz|onion
tamatar|tomato
gajar|carrot
mooli|radish
dhaniya|coriander|cilantro
pudina|mint
jeera|cumin
haldi|turmeric
adrak|ginger
lahsun|lehsun|garlic
imli|tamarind
gur|gud|jaggery
nariyal|coconut
kela|banana
aam|mango
amrood|guava
papita|papaya
kathal|jackfruit
sitaphal|custard apple|sugar apple
anar|pomegranate
tarbooz|watermelon
kharbooja|muskmelon|cantaloupe
seb|apple
angoor|grape
chikoo|chiku|sapota|sapodilla
jamun|java plum
amla|indian gooseberry
bajra|pearl millet
ragi|nachni|finger millet
jowar|sorghum
rajgira|amaranth
makka|makki|corn|maize
anda|egg
murgh|chicken
mutton|goat
machli|machhi|fish
jhinga|prawn|shrimp
doodh|milk
chai|tea
cheeni|sugar
chawal|rice
moongphali|groundnut|peanut
badam|almond
kaju|cashew
akhrot|walnut
kishmish|raisin
khajoor|date
pista|pistachio
alsi|flaxseed|linseed
til|sesame
sarson|mustard
saunf|fennel
elaichi|cardamom
dalchini|cinnamon
laung|clove
kali mirch|black pepper
mirch|chili|chilli|chile
nimbu|lemon|lime
kheera|kakdi|cucumber
kaddu|pumpkin
sahjan|moringa|drumstick leaves|drumstick pods
arbi|arvi|colocasia|taro
suran|jimikand|elephant foot yam
chukandar|beetroot|beet
shakarkandi|sweet potato
kheer|payasam|payasa
halwa|halva|sheera
idli|idly
dosa|dosai
vada|vadai|wada
sambar|sambhar
rasam|saaru
khichdi|khichri|khichadi
biryani|biriyani
pulao|pulav|pilaf|pilau
pakora|pakoda|bhajia|bajji
ladoo|laddu|laddoo
barfi|burfi
gulab jamun|gulab jamen
naan|nan
omelette|omelet|omlet
"""
SYN = [(re.compile(r"\b(?:%s)(?:s|es)?\b" % "|".join(map(re.escape, line.split("|"))), re.I),
        list(dict.fromkeys(re.findall(r"[a-z]+", line)))) for line in SYNONYMS.splitlines()]


def fetch(url, name):
    path = CACHE / name
    if not path.exists():
        print("downloading", url)
        CACHE.mkdir(parents=True, exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (wiggle build_foods.py)"})
        with urllib.request.urlopen(req) as r:
            data = r.read()
        path.with_suffix(".part").write_bytes(data)
        path.with_suffix(".part").replace(path)
    return path


def xlsx_rows(path, sheet):
    """Yield each row of one worksheet as a list of cell strings (None for empty). Minimal stdlib reader."""
    m = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")):
            shared.append("".join(t.text or "" for t in si.findall(m + "t") + si.findall(f"{m}r/{m}t")))
    rid = next(s.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
               for s in ET.fromstring(z.read("xl/workbook.xml")).iter(m + "sheet") if s.get("name") == sheet)
    target = next(r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels")) if r.get("Id") == rid)
    target = target.lstrip("/") if target.startswith("/") else "xl/" + target
    for row in ET.fromstring(z.read(target)).iter(m + "row"):
        cells = []
        for c in row.iter(m + "c"):
            col = 0
            for ch in re.match(r"[A-Z]+", c.get("r")).group():
                col = col * 26 + ord(ch) - 64
            v = c.find(m + "v")
            if c.get("t") == "s":
                val = shared[int(v.text)]
            elif c.get("t") == "inlineStr":
                val = "".join(t.text or "" for t in c.iter(m + "t"))
            else:
                val = v.text if v is not None else None
            cells += [None] * (col - 1 - len(cells)) + [val]
        yield cells


def num(v):
    """Source cell -> float. 'Tr' (trace) -> 0; blank/'N'/'NA' (unknown) -> None."""
    if v is None:
        return None
    v = str(v).strip()
    if v == "Tr":
        return 0.0
    try:
        return float(v)
    except ValueError:
        return None


def tidy(name):
    s = name.replace("\xa0", " ")
    s = re.sub(r"\s*\(Includes foods for USDA's Food Distribution Program\)", "", s)
    s = re.sub(r",\s*(with|without) (added )?salt( added)?\b", "", s)  # salt/vitamins don't change macros, so
    s = re.sub(r",\s*(un)?enriched\b", "", s)                         # stripping them merges such twins on dedupe
    s = re.sub(r",?\s*with(out)? added (vitamin [A-Z]( and vitamin [A-Z])?|ascorbic acid)\b", "", s)
    s = re.sub(r"\bbroilers? or fryers,\s*", "", s)
    s = re.sub(r",\s*mature seeds\b", "", s)
    s = re.sub(r",\s*(NFS|NS as to [^,]*)(?=,|$)", "", s)
    s = re.sub(r"^(Beverages|Spices|Nuts|Seeds),\s*", "", s)
    s = re.sub(r'"([A-Za-z][^"]*)"', r"\1", s)  # Chicken "wings" -> Chicken wings; keeps 1/8" inch marks
    s = re.sub(r"\s*,\s*", ", ", re.sub(r"\s+", " ", s)).strip(" ,.")
    outside = re.sub(r"\(.*?\)", "", s).split()
    if len(outside) > 1 and all(w[0].isupper() for w in outside if w[0].isalpha()):  # Title Case -> Sentence case
        s = s[0] + s[1:].lower()
    return s[:1].upper() + s[1:]


FRACTIONS = {0.25: "1/4", 0.33: "1/3", 0.5: "1/2", 0.67: "2/3", 0.75: "3/4"}
SKIP_PORTION = re.compile(r"\b(oz|ounces?|lbs?|pounds?|g|grams?|kg|quarts?|pints?|gallons?|liters?|ml|inch|inches"
                          r"|guideline|not specified|unit|nlea)\b|yield|=|\d\s*g\b", re.I)


def pick_portion(portions, kcal):
    """portions: [(label, grams, seq)] -> (label, grams) of the most household-like portion, or ('', 100)."""
    best = None
    for label, grams, seq in portions:
        label = re.sub(r'\(.*?\)|\(.*|"', "", label).split(",")[0].lower()
        label = re.sub(r"\s+", " ", label.replace("item", "piece")).strip()
        label = re.sub(r"^1 serving (?=\d)| (with|without) refuse", "", label)  # "1 serving 1 cup" -> "1 cup"
        if not label or not grams or not 0 < grams <= 500 or SKIP_PORTION.search(label):  # >500 g: whole roasts, pizzas
            continue
        if "medium" in label:
            rank = 0
        elif re.search(r"\b(tbsp|tablespoons?|tsp|teaspoons?)\b", label):
            rank = 2.5 if kcal >= 400 else 4  # oils, nuts, sugar: a spoon, not a cup
        elif re.search(r"\bcups?\b", label):
            rank = 3
        elif "serving" in label:
            rank = 2
        else:
            rank = 1  # countable: 1 large, 1 slice, 1 banana, 1 piece
        if best is None or (rank, seq) < best[0]:
            best = ((rank, seq), label, grams)
    return (best[1], best[2]) if best else ("", 100)


def usda(zipname, keep):
    """Yield rows from one FoodData Central CSV zip. keep(description, category) filters foods."""
    z = zipfile.ZipFile(fetch(FDC + zipname, zipname))

    def table(name):
        member = next(n for n in z.namelist() if n.endswith("/" + name))
        return csv.DictReader(io.TextIOWrapper(z.open(member), encoding="utf-8"))

    cats = {}
    for t, k, v in (("food_category.csv", "id", "description"),
                    ("wweia_food_category.csv", "wweia_food_category", "wweia_food_category_description")):
        if any(n.endswith("/" + t) for n in z.namelist()):
            cats.update((r[k], r[v]) for r in table(t))
    foods = {r["fdc_id"]: r for r in table("food.csv")
             if r["data_type"] in ("sr_legacy_food", "foundation_food", "survey_fndds_food")
             and keep(r["description"], cats.get(r["food_category_id"], ""))}
    wanted = {"1008": "kcal", "2048": "kcal2", "2047": "kcal3", "1003": "protein", "1004": "fat", "1085": "fat2",
              "1005": "carbs", "1050": "carbs2", "1079": "fiber",
              # FNDDS food_nutrient.csv uses the old nutrient numbers instead of ids
              "208": "kcal", "203": "protein", "204": "fat", "205": "carbs", "291": "fiber"}
    nut = {}
    for r in table("food_nutrient.csv"):
        if r["fdc_id"] in foods and r["nutrient_id"] in wanted and r["amount"] != "":
            nut.setdefault(r["fdc_id"], {})[wanted[r["nutrient_id"]]] = float(r["amount"])
    units = {r["id"]: r["name"] for r in table("measure_unit.csv")}
    portions = {}
    for r in table("food_portion.csv"):
        unit = units.get(r["measure_unit_id"], "")
        unit = "" if unit == "undetermined" else unit
        desc = r["portion_description"] or ("" if unit or r["modifier"].isdigit() else r["modifier"])
        amount = num(r["amount"])
        amount = "" if amount is None else FRACTIONS.get(round(amount, 2), f"{amount:g}")
        label = " ".join(x for x in (amount, unit, desc) if x)
        portions.setdefault(r["fdc_id"], []).append((label, num(r["gram_weight"]), num(r["seq_num"]) or 99))
    for fid, f in foods.items():
        n = nut.get(fid, {})
        kcal = n.get("kcal", n.get("kcal2", n.get("kcal3")))
        fat, carbs = n.get("fat", n.get("fat2")), n.get("carbs", n.get("carbs2"))
        if None in (kcal, n.get("protein"), fat, carbs):
            continue
        carbs = max(carbs, 0.0)  # "by difference" dips below 0 within analytical error for meats
        serving, grams = pick_portion(portions.get(fid, []), kcal)
        yield f["description"], kcal, n["protein"], carbs, fat, n.get("fiber"), serving, grams, "US"


def cofid():
    rows = xlsx_rows(fetch(COFID_URL, "CoFID_2021.xlsx"), "1.3 Proximates")
    col = {h: i for i, h in enumerate(next(rows)) if h}
    for r in rows:
        r += [None] * (max(col.values()) + 1 - len(r))
        if not re.fullmatch(r"\d+-\d+", r[col["Food Code"]] or ""):
            continue
        kcal, p, f, c = (num(r[col[k]]) for k in ("Energy (kcal) (kcal)", "Protein (g)", "Fat (g)", "Carbohydrate (g)"))
        if None in (kcal, p, f, c):
            continue
        fiber = num(r[col["AOAC fibre (g)"]])
        fiber = num(r[col["NSP (g)"]]) if fiber is None else fiber
        yield r[col["Food Name"]], kcal, p, c, f, fiber, "", 100, "UK"


def indb():
    rows = xlsx_rows(fetch(INDB_URL, "INDB.xlsx"), "Nutrient Data")
    h = {k: i for i, k in enumerate(next(rows))}
    for r in rows:
        r += [None] * (len(h) - len(r))
        g = lambda k: num(r[h[k]])
        kcal, p, c, f = g("energy_kcal"), g("protein_g"), g("carb_g"), g("fat_g")
        if None in (kcal, p, c, f) or kcal <= 0:
            continue
        # Raw-ingredient weights: >40 g fat/100 g means all the deep-frying oil was counted (poori at 738 kcal).
        # Energy far from 4/4/9 Atwater means the row's figures don't belong together (mostly soups).
        if f > 40 or abs(kcal - (4 * p + 4 * c + 9 * f)) > max(0.2 * kcal, 15):
            continue
        unit, per_serving = (r[h["servings_unit"]] or "").strip(), g("unit_serving_energy_kcal")
        if not unit:  # per the paper only "pickles and preserves" and weaning (baby) foods lack servings
            continue
        grams = per_serving / kcal * 100 if per_serving else 0
        serving, grams = ("1 " + unit, grams) if 0 < grams <= 500 else ("", 100)  # >500 g "bowls" are whole pots
        yield r[h["food_name"]], kcal, p, c, f, g("fibre_g"), serving, grams, "IN"


def aka_for(name):
    have, out = set(re.findall(r"[a-z]+", name.lower())), []
    for rx, words in SYN:
        if rx.search(name):
            out += [w for w in words if w not in have and w not in out]
    return " ".join(out)


def fmt(x, nd=1):
    s = f"{x:.{nd}f}".rstrip("0").rstrip(".") if nd else str(round(x))
    return "0" if s in ("-0", "") else s


def main():
    def us_keep(desc, cat):
        return not (cat in ("Baby Foods", "American Indian/Alaska Native Foods")
                    or cat.startswith(("Baby ", "Formula", "Human milk")))

    sources = [cofid(), usda(FNDDS, us_keep), usda(FOUNDATION, us_keep), usda(SR, us_keep)]
    if INCLUDE_INDB:
        sources.insert(0, indb())
    seen, out, counts = set(), [], {}
    for src in sources:
        for name, kcal, p, c, f, fiber, serving, grams, code in src:
            if JUNK.search(name) or BRAND.search(name):
                continue
            name = tidy(name)
            if not name or name.lower() in seen:
                continue
            seen.add(name.lower())
            row = [name, aka_for(name), fmt(kcal, 0), fmt(p), fmt(c), fmt(f), "" if fiber is None else fmt(fiber),
                   serving, fmt(grams), code]
            out.append([re.sub(r"[\t\r\n]+", " ", x).strip() for x in row])
            counts[code] = counts.get(code, 0) + 1
    text = "\t".join(HEADER) + "\n" + "".join("\t".join(r) + "\n" for r in out)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(text.encode("utf-8"), compresslevel=9, mtime=0))
    print(f"wrote {OUT} rows={len(out)} {counts} size={OUT.stat().st_size} bytes")
    check()


def check():
    lines = gzip.decompress(OUT.read_bytes()).decode("utf-8").split("\n")
    assert lines[0].split("\t") == HEADER and lines[-1] == "", "header/trailing newline"
    rows = [line.split("\t") for line in lines[1:-1]]
    assert len(rows) >= 8000, len(rows)
    assert OUT.stat().st_size <= 3 * 1024 * 1024
    by_name = {}
    for r in rows:
        assert len(r) == len(HEADER), r
        name, aka, kcal, p, c, f, fiber, serving, grams, src = r
        assert name and src in ("US", "UK", "IN") and not re.search(r"\d\s*g\b", serving), r
        assert 0 <= int(kcal) <= 950, r
        macros = [float(p), float(c), float(f)] + ([float(fiber)] if fiber else [])
        assert min(macros) >= 0 and sum(macros[:3]) <= 105, r
        assert float(grams) > 0, r
        by_name[name.lower()] = r
    assert len(by_name) == len(rows), "duplicate names"
    for name, lo, hi in [("Egg, whole, cooked, hard-boiled", 145, 165), ("Rice, white, long-grain, regular, cooked", 120, 140),
                         ("Bananas, raw", 85, 95), ("Bread, chapati or roti, plain, commercially prepared", 250, 320),
                         ("Masala dosa", 120, 260), ("Idli", 110, 160), ("Sambar", 40, 120)]:
        kcal = int(by_name[name.lower()][2])
        assert lo <= kcal <= hi, (name, kcal)
    print("checks ok")


if __name__ == "__main__":
    main()
