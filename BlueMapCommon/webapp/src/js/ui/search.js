/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import {i18n} from "../../i18n";
import {plainLabel} from "./MapUi";

const MAX_RESULTS = 8;

/**
 * Parses "x z" or "x y z" (separated by spaces, commas or slashes).
 * @returns {{x: number, y?: number, z: number}|null}
 */
export function parseCoordinates(query) {
    const match = /^\s*(-?\d+(?:\.\d+)?)[\s,/]+(-?\d+(?:\.\d+)?)(?:[\s,/]+(-?\d+(?:\.\d+)?))?\s*$/.exec(query || "");
    if (!match) return null;
    if (match[3] !== undefined) return {x: Number(match[1]), y: Number(match[2]), z: Number(match[3])};
    return {x: Number(match[1]), z: Number(match[2])};
}

export function matchScore(text, query) {
    if (!text || !query) return 0;
    const t = String(text).toLowerCase(), q = query.toLowerCase();
    if (t === q) return 100;
    if (t.startsWith(q)) return 80;
    if (t.split(/[\s·,/()-]+/).some(word => word.startsWith(q))) return 60;
    if (t.includes(q)) return 40;
    return 0;
}

/**
 * @param ui {MapUi}
 * @param query {string}
 */
export function search(ui, query) {
    const q = (query || "").trim();
    const results = [];
    if (!q) return results;
    const app = ui.app;

    const coordinates = parseCoordinates(q);
    if (coordinates) {
        results.push({
            id: "coordinates", score: 1000, icon: "near",
            title: ui.formatCoordinates(coordinates),
            subtitle: i18n.t("ui.search.goToCoordinates"),
            select: () => {
                const location = {x: Math.floor(coordinates.x), y: coordinates.y !== undefined ? Math.floor(coordinates.y) : null, z: Math.floor(coordinates.z), hires: false};
                ui.showLocation({...location, pin: true});
                // without a typed height the pin follows the terrain height once the camera arrived
                ui.flyTo(coordinates, {
                    distance: Math.min(app.mapViewer.controlsManager.distance, 200),
                    onDone: () => {
                        const card = ui.state.card;
                        if (location.y === null && card && card.type === "location" && card.x === location.x && card.z === location.z) ui.placePin(location);
                    }
                });
            }
        });
    }

    for (const map of app.maps) {
        const score = matchScore(map.data.name, q);
        if (score) results.push({
            id: `map:${map.data.id}`, score: score - 5, icon: "globe",
            title: map.data.name, subtitle: i18n.t("ui.search.world"),
            select: () => app.switchMap(map.data.id),
        });
    }

    const players = app.mapViewer.markers.markerSets.get("bm-players");
    if (players) for (const marker of players.markers.values()) {
        const score = matchScore(marker.data.name, q);
        if (score) results.push({
            id: `player:${marker.data.playerUuid}`, score: score + 2, image: ui.playerHead(marker.data), pixelated: true,
            title: marker.data.name, subtitle: marker.data.foreign ? i18n.t("ui.player.otherWorld") : i18n.t("ui.player.online"),
            select: () => {
                ui.openPlayer(marker.data);
                ui.focusPlayer(marker.data);
            }
        });
    }

    const visit = set => {
        for (const child of set.markerSets.values()) {
            if (child.data.id.startsWith("bm-")) continue;
            const setLabel = plainLabel(child.data.label, child.data.id);
            for (const marker of child.markers.values()) {
                if (!marker.data.listed) continue;
                const title = plainLabel(marker.data.label, "");
                const score = Math.max(matchScore(title, q), matchScore(setLabel, q) / 2);
                if (!title || !score) continue;
                results.push({
                    id: ui.markerKey(marker), score, icon: marker.isPoiMarker ? "place" : "layers",
                    title, subtitle: setLabel,
                    select: () => {
                        ui.openMarker(marker);
                        ui.flyTo(marker.data.position, {distance: Math.min(app.mapViewer.controlsManager.distance, 200)});
                    }
                });
            }
            visit(child);
        }
    };
    visit(app.mapViewer.markers);

    for (const [id, provider] of ui.searchProviders) {
        try {
            for (const result of provider(q) || []) {
                if (!result || !result.title || typeof result.select !== "function") continue;
                const score = result.score !== undefined ? result.score : Math.max(matchScore(result.title, q), matchScore(result.keywords, q) / 2);
                if (score > 0) results.push({...result, id: `${id}:${result.id || result.title}`, score: score + 1});
            }
        } catch (e) {
            console.error(`Search provider '${id}' failed`, e);
        }
    }

    const seen = new Set();
    return results
        .sort((a, b) => b.score - a.score || String(a.title).localeCompare(String(b.title)))
        .filter(result => !seen.has(result.id) && seen.add(result.id))
        .slice(0, MAX_RESULTS);
}
