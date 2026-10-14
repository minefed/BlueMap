# bluemap

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur) + [TypeScript Vue Plugin (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin).

## Customize configuration

See [Vite Configuration Reference](https://vitejs.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```

## Minefed map UI and add-on API

The Minefed build replaces the original control bar and side menu with a Google-Maps-like layout:
a search box, category chips, a place panel (bottom sheet on phones), a layer menu, map buttons,
a clicked-location card, a right-click menu, saved places, sharing and distance measurement.
The UI lives in `src/components/Map/` and its state in `src/js/ui/MapUi.js`.

Scripts registered through BlueMap's webapp `scripts` setting run after the map has loaded and
can use `window.bluemap.ui`. Every `register*` call returns a function that removes the entry again.

| Call | Purpose |
| --- | --- |
| `registerSearchProvider(id, query => results)` | Adds search results: `{id, title, subtitle?, icon?, color?, image?, keywords?, select()}` |
| `registerChip({id, label, icon, order, visible?, open})` | Adds a category chip next to the search box |
| `registerLayer({id, label, icon, order, get, set})` | Adds a toggle to the layer menu |
| `registerMarkerHandler((marker, event) => boolean)` | Handles marker clicks before the built-in place panel |
| `registerAddressProvider((x, z) => ({label, open?}))` | Names a position, e.g. its region |
| `registerResolver(prefix, (key, saved) => boolean)` | Re-opens saved places whose key starts with `prefix:` |
| `setStatus(id, {text, state})` | Shows a status line in the layer menu (`state`: `live`, `offline`, `idle`) |
| `openPlace(place)` / `openList(list)` / `closePanel()` | Shows content in the place panel |
| `flyTo({x, y, z}, {distance, onDone})`, `placePin({x, y, z})`, `highlightShape(points, y)`, `toast(text)`, `copy(text)` | Helpers |

A place is `{key?, kind?, title, subtitle?, badges?, icon?, color?, image?, position?, preview?,
description?, rows?, actions?, defaultActions?, live?, push?, mount?(element)}`. Fields may be
functions; `live: true` re-reads them every second. `mount` receives an empty element below the
header for custom DOM and may return a cleanup function. Keys are used for saved places, so use a
stable prefix such as `mtr-station:<id>`. Icon names are listed in `src/components/Map/Icon.vue`.
