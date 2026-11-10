#!/usr/bin/env python3
"""
Generuje migrację Flyway z grafem dróg na podstawie nodes.csv i edges.csv.

Dla każdej krawędzi skierowanej (drogi dwukierunkowe -> dwie krawędzie A->B i B->A)
wysyła jedno zapytanie do openrouteservice (profil driving-hgv, ciężarówka) i pobiera:
  - długość trasy [km],
  - czas wzorcowy [min] (reference_time_min, tylko do kalibracji),
  - linię drogi (geometry) do rysowania na mapie.

Klasa drogi, nawierzchnia, teren i ograniczenia pochodzą z edges.csv (ocena ekspercka).

Odpowiedzi API są zapisywane w katalogu cache/, więc ponowne uruchomienie
nie wysyła zapytań drugi raz (można np. poprawić CSV i wygenerować SQL bez API).

Uruchomienie (z katalogu tools/road-graph):
    export ORS_API_KEY=...        # klucza nie zapisujemy w repozytorium
    python3 build_graph.py
Wynik: backend/src/main/resources/db/migration/V5__road_graph.sql
"""

import csv
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
CACHE_DIR = HERE / "cache"
OUTPUT = HERE.parent.parent / "backend/src/main/resources/db/migration/V5__road_graph.sql"

ORS_URL = "https://api.openrouteservice.org/v2/directions/driving-hgv/geojson"
# Darmowy plan: 40 zapytań na minutę -> co najmniej 1,5 s przerwy
PAUSE_BETWEEN_REQUESTS_S = 1.6
# Jak daleko (m) ORS może przesunąć punkt, aby trafić na drogę dostępną dla ciężarówki
SNAP_RADIUS_M = 1000
# Liczba miejsc po przecinku we współrzędnych geometrii (5 miejsc ~ 1 m)
COORD_DECIMALS = 5

# Progi ostrzeżeń w raporcie kontrolnym
MAX_ROAD_TO_STRAIGHT_RATIO = 1.6   # droga dłuższa niż 1,6 x linia prosta -> podejrzane
MAX_DIRECTION_DIFF = 0.10          # A->B i B->A różnią się o więcej niż 10% -> podejrzane


def read_csv(name):
    with open(HERE / name, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def haversine_km(lat1, lon1, lat2, lon2):
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def directed_edges(edges):
    """Rozwija drogi z CSV w krawędzie skierowane; droga dwukierunkowa daje A->B i B->A."""
    result = []
    for e in edges:
        result.append((e["from"], e["to"], e))
        if e["two_way"].strip().lower() == "true":
            result.append((e["to"], e["from"], e))
    return result


def cache_file(from_name, to_name):
    safe = f"{from_name}__{to_name}".replace(" ", "_")
    return CACHE_DIR / f"{safe}.json"


def fetch_route(api_key, a, b):
    """Jedno zapytanie do ORS: trasa ciężarówki z węzła a do węzła b."""
    body = {
        "coordinates": [[float(a["lon"]), float(a["lat"])], [float(b["lon"]), float(b["lat"])]],
        "radiuses": [SNAP_RADIUS_M, SNAP_RADIUS_M],
        "geometry_simplify": True,
    }
    request = urllib.request.Request(
        ORS_URL,
        data=json.dumps(body).encode("utf-8"),
        headers={"Authorization": api_key, "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def get_route(api_key, nodes_by_name, from_name, to_name):
    """Zwraca odpowiedź ORS z cache albo pobiera ją z API i zapisuje do cache."""
    path = cache_file(from_name, to_name)
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8")), False
    if not api_key:
        sys.exit(f"Brak cache dla {from_name} -> {to_name} i brak zmiennej ORS_API_KEY")
    try:
        data = fetch_route(api_key, nodes_by_name[from_name], nodes_by_name[to_name])
    except urllib.error.HTTPError as ex:
        details = ex.read().decode("utf-8", errors="replace")
        sys.exit(f"Błąd ORS dla {from_name} -> {to_name}: HTTP {ex.code}\n{details}")
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data, True


def sql_str(value):
    return "'" + value.replace("'", "''") + "'"


def sql_num(value):
    return value.strip() if value and value.strip() else "NULL"


def node_id(name):
    return f"(SELECT id FROM road_node WHERE name = {sql_str(name)})"


def main():
    api_key = os.environ.get("ORS_API_KEY", "").strip()
    nodes = read_csv("nodes.csv")
    edges = read_csv("edges.csv")
    nodes_by_name = {n["name"]: n for n in nodes}
    CACHE_DIR.mkdir(exist_ok=True)

    directed = directed_edges(edges)
    results = {}
    for i, (from_name, to_name, edge) in enumerate(directed, start=1):
        data, fetched = get_route(api_key, nodes_by_name, from_name, to_name)
        feature = data["features"][0]
        summary = feature["properties"]["summary"]
        coords = [[round(lon, COORD_DECIMALS), round(lat, COORD_DECIMALS)]
                  for lon, lat in feature["geometry"]["coordinates"]]
        results[(from_name, to_name)] = {
            "edge": edge,
            "distance_km": round(summary["distance"] / 1000.0, 1),
            "reference_time_min": max(1, round(summary["duration"] / 60.0)),
            "geometry": json.dumps(coords, separators=(",", ":")),
            "points": len(coords),
        }
        print(f"[{i:3}/{len(directed)}] {from_name} -> {to_name}: "
              f"{results[(from_name, to_name)]['distance_km']} km "
              f"({'API' if fetched else 'cache'})")
        if fetched:
            time.sleep(PAUSE_BETWEEN_REQUESTS_S)

    print_report(nodes_by_name, results)
    write_migration(nodes, directed, results)


def print_report(nodes_by_name, results):
    """Raport kontrolny: podejrzane krawędzie do ręcznego sprawdzenia."""
    print("\n=== Raport kontrolny ===")
    warnings = 0
    for (a, b), r in results.items():
        na, nb = nodes_by_name[a], nodes_by_name[b]
        straight = haversine_km(float(na["lat"]), float(na["lon"]), float(nb["lat"]), float(nb["lon"]))
        ratio = r["distance_km"] / straight
        if ratio > MAX_ROAD_TO_STRAIGHT_RATIO:
            warnings += 1
            print(f"OBJAZD?   {a} -> {b}: {r['distance_km']} km drogą, {straight:.1f} km w linii prostej "
                  f"(x{ratio:.2f})")
        back = results.get((b, a))
        if back and a < b:
            diff = abs(r["distance_km"] - back["distance_km"]) / max(r["distance_km"], back["distance_km"])
            if diff > MAX_DIRECTION_DIFF:
                warnings += 1
                print(f"KIERUNKI? {a} <-> {b}: {r['distance_km']} km vs {back['distance_km']} km")
    total_points = sum(r["points"] for r in results.values())
    print(f"Krawędzi: {len(results)}, punktów geometrii: {total_points}, ostrzeżeń: {warnings}")


def write_migration(nodes, directed, results):
    lines = [
        "-- WYGENEROWANO AUTOMATYCZNIE skryptem tools/road-graph/build_graph.py "
        f"({date.today().isoformat()}). Nie edytować ręcznie.",
        "-- Źródła: węzły i atrybuty dróg - tools/road-graph/*.csv (ocena ekspercka);",
        "-- distance_km, reference_time_min, geometry - openrouteservice, profil driving-hgv.",
        "",
        "-- Nowe węzły grafu. Miasta z V3 już istnieją (nazwa jest unikalna) - są pomijane.",
        "INSERT INTO road_node (name, lat, lon, default_handling_min)",
        "VALUES",
    ]
    node_rows = [f"    ({sql_str(n['name'])}, {n['lat']}, {n['lon']}, {n['default_handling_min']})"
                 for n in nodes]
    lines.append(",\n".join(node_rows))
    lines.append("ON CONFLICT (name) DO NOTHING;")
    lines.append("")
    lines.append("-- Krawędzie skierowane: każda droga dwukierunkowa zapisana jako A->B i B->A.")

    for from_name, to_name, edge in directed:
        r = results[(from_name, to_name)]
        lines.append("")
        lines.append(f"-- {from_name} -> {to_name} ({edge['road']})")
        if edge.get("note", "").strip():
            lines.append(f"-- {edge['note'].strip()}")
        lines.append(
            "INSERT INTO road_edge (from_node_id, to_node_id, distance_km, road_type, surface_quality, terrain,\n"
            "                       max_weight_t, max_height_m, geometry, reference_time_min)\n"
            f"VALUES ({node_id(from_name)}, {node_id(to_name)}, {r['distance_km']},\n"
            f"        {sql_str(edge['road_type'])}, {sql_str(edge['surface_quality'])}, {sql_str(edge['terrain'])},\n"
            f"        {sql_num(edge.get('max_weight_t'))}, {sql_num(edge.get('max_height_m'))},\n"
            f"        {sql_str(r['geometry'])}::jsonb, {r['reference_time_min']});"
        )

    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    size_kb = OUTPUT.stat().st_size / 1024
    print(f"\nZapisano {OUTPUT.relative_to(HERE.parent.parent)} ({size_kb:.0f} KB)")


if __name__ == "__main__":
    main()
