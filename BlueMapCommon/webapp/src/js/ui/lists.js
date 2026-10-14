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
import {horizontalDistance, plainLabel} from "./MapUi";

/** Marker sets shown to users: everything except BlueMap's internal "bm-" sets. */
export function userMarkerSets(set) {
    return [...set.markerSets.values()]
        .filter(child => !child.data.id.startsWith("bm-") && child.data.listed)
        .sort((a, b) => (a.data.sorting || 0) - (b.data.sorting || 0) || String(a.data.label).localeCompare(String(b.data.label)));
}

export function hasListedMarkers(set) {
    for (const marker of set.markers.values()) if (marker.data.listed) return true;
    for (const child of set.markerSets.values()) if (!child.data.id.startsWith("bm-") && hasListedMarkers(child)) return true;
    return false;
}

function countMarkers(set) {
    let count = 0;
    for (const marker of set.markers.values()) if (marker.data.listed) count++;
    for (const child of set.markerSets.values()) count += countMarkers(child);
    return count;
}

function distanceMeta(ui, position) {
    if (!position) return "";
    const center = ui.app.mapViewer.controlsManager.position;
    return ui.formatBlocks(horizontalDistance(position, center));
}

/** @param ui {MapUi} */
export function playersList(ui, push = false) {
    return {
        id: "players",
        chipId: "players",
        title: i18n.t("players.title"),
        push,
        live: true,
        empty: i18n.t("ui.player.none"),
        items: () => {
            const set = ui.app.mapViewer.markers.markerSets.get("bm-players");
            if (!set) return [];
            return [...set.markers.values()]
                .map(marker => marker.data)
                .sort((a, b) => String(a.name).localeCompare(String(b.name)))
                .map(player => {
                    const following = ui.isFollowing(player.playerUuid);
                    const address = !player.foreign && ui.addressAt(player.position.x, player.position.z);
                    return {
                        key: `player:${player.playerUuid}`,
                        title: player.name,
                        subtitle: player.foreign ? i18n.t("ui.player.otherWorld") : (address ? address.label : ui.mapName()),
                        image: ui.playerHead(player),
                        pixelated: true,
                        onClick: () => {
                            ui.openPlayer(player, true);
                            ui.focusPlayer(player);
                        },
                        action: {
                            label: following ? i18n.t("ui.player.following") : i18n.t("ui.player.follow"),
                            icon: "target",
                            active: following,
                            onClick: () => ui.toggleFollow(player),
                        },
                    };
                });
        },
    };
}

/** @param ui {MapUi} */
export function markerSetList(ui, set = null, push = false) {
    const root = set || ui.app.mapViewer.markers;
    const title = set ? plainLabel(set.data.label, set.data.id) : i18n.t("markers.title");
    return {
        id: `markers:${root.data.id}`,
        chipId: set ? null : "markers",
        title,
        push,
        empty: i18n.t("ui.list.empty"),
        footnote: i18n.t("ui.list.distanceNote"),
        items: () => {
            const items = [];
            for (const child of userMarkerSets(root)) {
                if (!hasListedMarkers(child) && !child.data.toggleable) continue;
                items.push({
                    key: `set:${child.data.id}`,
                    title: plainLabel(child.data.label, child.data.id),
                    subtitle: i18n.t("ui.list.markerCount", {count: countMarkers(child)}),
                    icon: "layers",
                    color: "#7b5ea7",
                    muted: !child.visible,
                    onClick: () => ui.openList(markerSetList(ui, child, true)),
                    action: child.data.toggleable ? {
                        label: child.visible ? i18n.t("ui.layers.shown") : i18n.t("ui.layers.hidden"),
                        icon: child.visible ? "layers" : "close",
                        active: child.visible,
                        onClick: () => {
                            child.visible = !child.visible;
                            child.data.saveState();
                            ui.app.mapViewer.redraw();
                        },
                    } : null,
                });
            }
            const markers = [...root.markers.values()]
                .filter(marker => marker.data.listed)
                .map(marker => ({marker, title: plainLabel(marker.data.label, marker.data.id)}))
                .sort((a, b) => (a.marker.data.sorting || 0) - (b.marker.data.sorting || 0) || a.title.localeCompare(b.title));
            for (const {marker, title} of markers) {
                items.push({
                    key: ui.markerKey(marker),
                    title,
                    subtitle: marker.data.type === "poi" ? i18n.t("ui.place.marker") : i18n.t("ui.place.area"),
                    icon: marker.isPoiMarker ? "place" : "layers",
                    color: "#e8710a",
                    meta: distanceMeta(ui, marker.data.position),
                    muted: !root.visible,
                    onClick: () => {
                        ui.openMarker(marker, true);
                        ui.flyTo(marker.data.position, {distance: Math.min(ui.app.mapViewer.controlsManager.distance, 200)});
                    },
                });
            }
            return items;
        },
    };
}

/** @param ui {MapUi} */
export function savedList(ui, push = false) {
    return {
        id: "saved",
        chipId: "saved",
        title: i18n.t("ui.saved.title"),
        push,
        empty: i18n.t("ui.saved.empty"),
        footnote: i18n.t("ui.saved.storageNote"),
        items: () => ui.state.saved.map(entry => {
            const map = ui.app.mapsMap.get(entry.mapId);
            return {
                key: entry.key,
                title: entry.title,
                subtitle: [entry.kind, map ? map.data.name : null, ui.formatCoordinates(entry)].filter(Boolean).join(" · "),
                icon: "bookmark",
                color: "#1a73e8",
                onClick: () => ui.openSaved(entry),
                action: {label: i18n.t("ui.saved.remove"), icon: "close", onClick: () => ui.removeSaved(entry.key)},
            };
        }),
    };
}
