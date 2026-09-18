#!/usr/bin/env bash
set -euo pipefail

mkdir -p src/main/resources/data/guides

curl -sG -A "trail-assistant/1.0" https://overpass-api.de/api/interpreter \
  --data-urlencode 'data=[out:json][timeout:60][bbox:-23.05,-43.45,-22.85,-43.15];
    way["highway"~"path|footway"]["name"]["sac_scale"];
    out tags;' \
| jq -r '
  def difficulty:
    if . == null then "moderate"
    elif (. == "strolling" or . == "hiking") then "easy"
    elif . == "mountain_hiking" then "moderate"
    else "hard" end;
  ["name","region","difficulty","distanceKm"],
  (.elements[]
   | select(.tags.name != null)
   | [ .tags.name,
       "Rio de Janeiro",
       ( .tags.sac_scale | difficulty ),
       ( .tags.distance // "" | sub(" km$"; "") ) ])
  | @csv' > src/main/resources/data/trails.csv

for title in "Pedra da Gávea" "Pico da Tijuca" "Pedra Bonita" "Morro da Urca"; do
  slug=$(printf '%s' "$title" | iconv -f utf-8 -t ascii//TRANSLIT | tr '[:upper:] ' '[:lower:]-')
  curl -sG "https://pt.wikipedia.org/w/api.php" \
    --data-urlencode "action=query" \
    --data-urlencode "prop=extracts" \
    --data-urlencode "explaintext=1" \
    --data-urlencode "format=json" \
    --data-urlencode "titles=$title" \
  | jq -r '.query.pages[] | select(.extract != null) | .extract' > "src/main/resources/data/guides/$slug.md"
done