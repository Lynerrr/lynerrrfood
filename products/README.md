# Packaged products database

Popular packaged foods and drinks (Coca-Cola, Oreo, Snickers, local brands…) for the LynerrrFood app,
kept separate from the health-diet.ru food data.

## Source and license

Data from [Open Food Facts](https://openfoodfacts.org) contributors, made available under the
[Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/). This derived database is
published under the same license: you may share and adapt it, as long as you credit Open Food Facts and keep
derived databases under the ODbL.

Products are the most scanned ones in Russia and Kazakhstan (Open Food Facts popularity lists). Entries with
missing macros or numbers that don't add up are dropped, and duplicates (same brand and name under several
barcodes) are merged.

## Files

- `products.json`: manifest the app checks for updates:
  ```json
  { "version": "20261020130000", "products": 2500, "data": "products.dat", "source": "...", "license": "..." }
  ```
  `version` is a timestamp; the app downloads `data` (relative to the manifest URL) when it's newer than
  what it has.
- `products.dat`: gzipped UTF-8 TSV:
  ```
  V  version
  L  attribution / license
  N  index  nutrient name  unit                  (nutrient names and units as on health-diet.ru)
  P  barcode  categoryId  scans  brand  name  servingGrams  index:amount ...   (amounts per 100 g / 100 ml)
  ```

## Updating

1. `./gradlew :scraper:runProducts` rebuilds both files (and the copy bundled in the app).
2. Commit and push this folder.
3. Build the app with the manifest URL, e.g. in `gradle.properties`:
   `productsManifestUrl=https://raw.githubusercontent.com/<user>/<repo>/main/products/products.json`.
   Settings → Packaged products → Check for updates then pulls new versions without reinstalling.

Products already in someone's diary keep their identity across updates (matched by barcode).
