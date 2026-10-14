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
import {markRaw, reactive} from "vue";
import {MathUtils, Raycaster, Vector2, Vector3} from "three";
import {animate, EasingFunctions, elementOffset} from "../util/Utils";
import {getLocalStorage, setLocalStorage} from "../Utils";
import {Marker} from "../markers/Marker";
import {MarkerSet} from "../markers/MarkerSet";
import {TilePreview} from "./TilePreview";
import {i18n} from "../../i18n";

const SAVED_STORAGE_KEY = "bluemap-saved-places";
const PANEL_WIDTH = 424;
const MOBILE_BREAK = 640;

/** Strips the HTML of a marker label down to its first text, like the old marker list did. */
export function plainLabel(label, fallback = "") {
    if (!label) return fallback;
    const match = /^(?:<[^>]*>\s*)*([^<>]*\S[^<>]*)(?:<|$)/i.exec(label);
    return match && match.length > 1 ? match[1].trim() : fallback;
}

export function horizontalDistance(a, b) {
    return Math.hypot(a.x - b.x, a.z - b.z);
}

/**
 * Google-Maps-like UI state and the extension API that add-on scripts (registered through
 * BlueMap's webapp scripts) use to add search results, chips, layers and place details.
 *
 * Add-ons find it at <code>window.bluemap.ui</code> once BlueMap finished loading.
 */
export class MapUi {

    /**
     * @param app {BlueMapApp}
     */
    constructor(app) {
        this.app = app;

        this.state = reactive({
            panel: null,
            panelStack: [],
            panelCollapsed: false,
            sheetExpanded: false,
            chip: null,
            query: "",
            drawerOpen: false,
            layersOpen: false,
            card: null,
            measure: null,
            share: null,
            contextMenu: null,
            toast: null,
            saved: [],
            cursor: null,
            selectedKey: null,
            revision: 0,
            tick: 0,
            statuses: {},
        });

        this.searchProviders = new Map();
        this.chips = reactive([]);
        this.layers = reactive([]);
        this.markerHandlers = [];
        this.addressProviders = [];
        this.resolvers = new Map();
        this.preview = new TilePreview(app);

        this.toastTimeout = null;
        this.flyAnimation = null;
        this.raycaster = new Raycaster();
        this.raycaster.layers.enableAll();

        this.overlay = null;
        this.loadSaved();
    }

    /**
     * Called by BlueMapApp after the viewer exists. Hooks marker clicks and creates the overlay marker-set.
     */
    install() {
        const viewer = this.app.mapViewer;

        this.overlay = new MarkerSet("bm-ui-overlay");
        this.overlay.data.toggleable = false;
        this.overlay.visible = true;
        viewer.markers.add(this.overlay);

        // markers that don't handle their own clicks bubble up to the root set
        Marker.clickHandler = (marker, event) => this.handleMarkerClick(marker, event);
        viewer.markers.onClick = event => {
            const marker = (event.eventStack || []).find(object => object.isMarker);
            return marker ? this.handleMarkerClick(marker, event) : false;
        };

        // live views (players, arrivals, marker lists) re-read their data once per second
        setInterval(() => this.state.tick++, 1000);

        this.app.events.addEventListener("bluemapMapChanged", () => {
            this.clearMeasure();
            this.closeCard();
            this.clearHighlight();
            this.state.revision++;
        });
    }

    // ---------------------------------------------------------------- extension API

    /**
     * @param id {string}
     * @param provider {function(query: string): Array<{id: string, title: string, subtitle?: string, icon?: string, color?: string, image?: string, select: function}>}
     * @returns {function} unregister
     */
    registerSearchProvider(id, provider) {
        this.searchProviders.set(id, provider);
        return () => this.searchProviders.delete(id);
    }

    /**
     * @param chip {{id: string, label: string|function, icon?: string, order?: number, visible?: function(): boolean, open: function}}
     * @returns {function} unregister
     */
    registerChip(chip) {
        this.unregisterById(this.chips, chip.id);
        this.chips.push(markRaw({order: 50, ...chip}));
        this.chips.sort((a, b) => a.order - b.order);
        return () => this.unregisterById(this.chips, chip.id);
    }

    /**
     * @param layer {{id: string, label: string|function, icon?: string, order?: number, get: function(): boolean, set: function(boolean)}}
     * @returns {function} unregister
     */
    registerLayer(layer) {
        this.unregisterById(this.layers, layer.id);
        this.layers.push(markRaw({order: 50, ...layer}));
        this.layers.sort((a, b) => a.order - b.order);
        return () => this.unregisterById(this.layers, layer.id);
    }

    /**
     * A handler receives every clicked marker first and returns true when it opened its own details.
     * @param handler {function(marker: Marker, event: object): boolean}
     * @returns {function} unregister
     */
    registerMarkerHandler(handler) {
        this.markerHandlers.push(handler);
        return () => this.markerHandlers.splice(this.markerHandlers.indexOf(handler) >>> 0, 1);
    }

    /**
     * @param provider {function(x: number, z: number): ({label: string, open?: function}|null)}
     * @returns {function} unregister
     */
    registerAddressProvider(provider) {
        this.addressProviders.push(provider);
        return () => this.addressProviders.splice(this.addressProviders.indexOf(provider) >>> 0, 1);
    }

    /**
     * Re-opens a saved place by its key prefix (the part before the first ':').
     * @param prefix {string}
     * @param resolver {function(key: string, saved: object): boolean}
     * @returns {function} unregister
     */
    registerResolver(prefix, resolver) {
        this.resolvers.set(prefix, resolver);
        return () => this.resolvers.delete(prefix);
    }

    /**
     * Shows a short status line in the layer menu, e.g. the state of a live data feed.
     * @param id {string}
     * @param status {{text: string, state?: string}|null}
     */
    setStatus(id, status) {
        if (status) this.state.statuses[id] = {text: status.text, state: status.state || "idle"};
        else delete this.state.statuses[id];
    }

    /** Asks list and search views to re-read provider data. */
    refresh() {
        this.state.revision++;
    }

    unregisterById(list, id) {
        const index = list.findIndex(entry => entry.id === id);
        if (index >= 0) list.splice(index, 1);
    }

    // ---------------------------------------------------------------- panel

    /**
     * Opens a place in the side panel (bottom sheet on phones).
     * @param place {{
     *   key?: string, kind?: string, title: string, subtitle?: string|function,
     *   badges?: Array<{label: string, color?: string}>, image?: string, icon?: string, color?: string,
     *   position?: {x: number, y?: number, z: number}, preview?: boolean,
     *   description?: string, html?: string, rows?: Array|function,
     *   actions?: Array|function, defaultActions?: boolean, tabs?: Array, live?: boolean,
     *   mount?: function(Element): (function|void), onClose?: function, push?: boolean
     * }}
     */
    openPlace(place) {
        if (this.state.card && this.state.card.type === "location") this.closeCard();
        this.openView({view: "place", ...place}, place.push);
        this.state.selectedKey = place.key || null;
        this.state.query = place.title || "";
    }

    /**
     * @param list {{id?: string, title: string, subtitle?: string, items: Array|function, empty?: string, footnote?: string, push?: boolean}}
     */
    openList(list) {
        this.openView({view: "list", ...list}, list.push);
    }

    openSettings() {
        this.openView({view: "settings", title: i18n.t("settings.title")}, false);
    }

    openHelp() {
        this.openView({view: "help", title: i18n.t("info.title")}, false);
    }

    openView(view, push = false) {
        const current = this.state.panel;
        if (current && current.onClose && !push) current.onClose();
        if (push && current) this.state.panelStack.push(current);
        else this.state.panelStack.splice(0);
        if (!push) this.state.chip = view.chipId || null;
        this.state.panel = markRaw({...view, openedAt: Date.now()});
        this.state.panelCollapsed = false;
        this.closeContextMenu();
        if (view.view !== "list" && view.view !== "place") this.state.selectedKey = null;
    }

    /** Re-renders the open panel without changing the navigation stack. */
    updatePanel(patch = {}) {
        if (!this.state.panel) return;
        this.state.panel = markRaw({...this.state.panel, ...patch});
    }

    panelBack() {
        const current = this.state.panel;
        if (current && current.onClose) current.onClose();
        const previous = this.state.panelStack.pop();
        this.state.panel = previous || null;
        this.state.selectedKey = previous && previous.key || null;
        if (!previous) this.state.chip = null;
    }

    closePanel() {
        const current = this.state.panel;
        if (current && current.onClose) current.onClose();
        this.state.panelStack.forEach(view => view.onClose && view.onClose());
        this.state.panelStack.splice(0);
        this.state.panel = null;
        this.state.chip = null;
        this.state.selectedKey = null;
        this.state.sheetExpanded = false;
        this.clearHighlight();
    }

    // ---------------------------------------------------------------- built-in places

    handleMarkerClick(marker, event) {
        if (event && event.data && event.data.doubleTap) return false;
        for (const handler of [...this.markerHandlers]) {
            try {
                if (handler(marker, event)) return true;
            } catch (e) {
                console.error("Marker handler failed", e);
            }
        }

        if (marker.isPlayerMarker) {
            this.openPlayer(marker.data);
            return true;
        }

        if ((marker.isPoiMarker || marker.isObjectMarker) && (marker.data.label || marker.data.detail)) {
            this.openMarker(marker);
            return true;
        }

        return false;
    }

    markerSetOf(marker) {
        let parent = marker.parent;
        while (parent && !parent.isMarkerSet) parent = parent.parent;
        return parent;
    }

    markerKey(marker) {
        const set = this.markerSetOf(marker);
        return `marker:${set ? set.data.id : ""}:${marker.data.id}`;
    }

    openMarker(marker, push = false) {
        const data = marker.data, set = this.markerSetOf(marker);
        const title = plainLabel(data.label, data.id);
        const position = {x: data.position.x, y: data.position.y, z: data.position.z};
        const actions = [];
        if (data.link) {
            actions.push({icon: "open", label: i18n.t("ui.place.openLink"), onClick: () => window.open(data.link, data.newTab ? "_blank" : "_self")});
        }
        const detail = data.detail && data.detail !== data.label ? data.detail : null;
        this.openPlace({
            key: this.markerKey(marker),
            kind: set ? plainLabel(set.data.label, set.data.id) : i18n.t("ui.place.marker"),
            title,
            icon: marker.isPoiMarker ? "place" : "layers",
            position,
            html: detail,
            actions,
            rows: () => this.positionRows(position, set),
            push,
        });
    }

    openPlayer(player, push = false) {
        const playerData = () => this.playerMarkerData(player.playerUuid) || player;
        this.openPlace({
            key: `player:${player.playerUuid}`,
            kind: i18n.t("players.title"),
            title: player.name,
            image: this.playerHead(player),
            pixelated: true,
            live: true,
            push,
            position: () => playerData().position,
            subtitle: () => playerData().foreign ? i18n.t("ui.player.otherWorld") : i18n.t("ui.player.online"),
            defaultActions: false,
            actions: () => {
                const following = this.isFollowing(player.playerUuid);
                return [
                    {icon: "target", label: following ? i18n.t("ui.player.following") : i18n.t("ui.player.follow"), primary: !following, active: following, onClick: () => this.toggleFollow(playerData())},
                    {icon: "near", label: i18n.t("ui.place.moveTo"), onClick: () => this.focusPlayer(playerData())},
                    {icon: "copy", label: i18n.t("ui.place.copyCoordinates"), onClick: () => this.copyCoordinates(playerData().position)},
                ];
            },
            rows: () => playerData().foreign ? [{icon: "globe", text: i18n.t("ui.player.otherWorld")}] : this.positionRows(playerData().position),
        });
    }

    playerMarkerData(uuid) {
        const set = this.app.mapViewer.markers.markerSets.get("bm-players");
        if (!set) return null;
        for (const marker of set.markers.values()) if (marker.data.playerUuid === uuid) return marker.data;
        return null;
    }

    playerHead(player) {
        const map = this.app.mapViewer.map;
        return map ? `${map.data.dataUrl}assets/playerheads/${player.playerUuid}.png` : "assets/steve.png";
    }

    isFollowing(uuid) {
        const controls = this.app.mapViewer.controlsManager.data.controls;
        return !!(controls && controls.followingPlayer && controls.followingPlayer.playerUuid === uuid);
    }

    async toggleFollow(player) {
        const cm = this.app.mapViewer.controlsManager;
        if (this.isFollowing(player.playerUuid)) {
            if (cm.controls && cm.controls.stopFollowingPlayerMarker) cm.controls.stopFollowingPlayerMarker();
            this.updatePanel();
            return;
        }
        if (player.foreign) {
            const map = await this.app.findPlayerMap(player.playerUuid);
            if (!map) return this.toast(i18n.t("ui.player.notFound"));
            await this.app.switchMap(map.data.id, false);
            player = this.playerMarkerData(player.playerUuid) || player;
        }
        if (this.app.appState.controls.state !== "perspective" && this.app.appState.controls.state !== "flat") this.app.setPerspectiveView(500);
        const controls = cm.controls;
        if (controls && controls.followPlayerMarker) {
            controls.followPlayerMarker(player);
            this.toast(i18n.t("ui.player.followHint"));
        }
        this.updatePanel();
    }

    focusPlayer(player) {
        if (!player || player.foreign) return;
        this.flyTo(player.position, {distance: Math.min(this.app.mapViewer.controlsManager.distance, 120)});
    }

    positionRows(position, set = null) {
        if (!position) return [];
        const rows = [{
            icon: "place",
            text: this.formatCoordinates(position),
            sub: `${this.mapName()} · ${i18n.t("ui.place.clickToCopy")}`,
            mono: true,
            onClick: () => this.copyCoordinates(position),
        }];
        const address = this.addressAt(position.x, position.z);
        if (address) rows.push({icon: "region", text: address.label, sub: i18n.t("ui.region.label"), onClick: address.open});
        if (set) rows.push({icon: "layers", text: plainLabel(set.data.label, set.data.id), sub: i18n.t("ui.place.markerSet")});
        return rows;
    }

    // ---------------------------------------------------------------- camera

    mapName() {
        const map = this.app.mapViewer.map;
        return map ? map.data.name : "";
    }

    /**
     * World units covered by one screen pixel at the camera target.
     */
    worldPerPixel() {
        const viewer = this.app.mapViewer, cm = viewer.controlsManager, camera = viewer.camera;
        const height = viewer.rootElement.clientHeight || 1;
        const distance = Math.max(cm.distance, 1);
        const fov = MathUtils.degToRad(camera.fov || 75);
        return 2 * distance * Math.tan(fov / 2) / height;
    }

    /**
     * Moves the camera so that the position is visible next to the open panel / above the bottom sheet.
     * @param position {{x: number, y?: number, z: number}}
     * @param options {{distance?: number, duration?: number, onDone?: function}}
     */
    flyTo(position, options = {}) {
        if (!position) return;
        const viewer = this.app.mapViewer, cm = viewer.controlsManager;
        if (cm.controls && cm.controls.stopFollowingPlayerMarker) cm.controls.stopFollowingPlayerMarker();
        if (this.flyAnimation) this.flyAnimation.cancel();

        const startX = cm.position.x, startZ = cm.position.z, startDistance = cm.distance;
        const targetDistance = options.distance !== undefined ? MathUtils.clamp(options.distance, this.app.mapControls.minDistance || 5, this.app.mapControls.maxDistance || 100000) : startDistance;

        // offset the camera center so the target isn't hidden behind the panel
        const width = viewer.rootElement.clientWidth;
        const panelOpen = this.state.panel && !this.state.panelCollapsed;
        let offsetRight = 0, offsetUp = 0;
        if (panelOpen && width > MOBILE_BREAK) offsetRight = PANEL_WIDTH / 2;
        else if (panelOpen) offsetUp = viewer.rootElement.clientHeight * 0.22;
        const wpp = this.worldPerPixel() * targetDistance / Math.max(cm.distance, 1);
        const r = cm.rotation;
        const rightX = Math.cos(r), rightZ = Math.sin(r), upX = Math.sin(r), upZ = -Math.cos(r);
        // target = center + right * offsetRight + up * offsetUp (in pixels)
        const targetX = position.x - (rightX * offsetRight + upX * offsetUp) * wpp;
        const targetZ = position.z - (rightZ * offsetRight + upZ * offsetUp) * wpp;

        this.flyAnimation = animate(p => {
            const ep = EasingFunctions.easeInOutQuad(p);
            cm.position.x = MathUtils.lerp(startX, targetX, ep);
            cm.position.z = MathUtils.lerp(startZ, targetZ, ep);
            cm.distance = MathUtils.lerp(startDistance, targetDistance, ep);
        }, options.duration !== undefined ? options.duration : 600, finished => {
            this.flyAnimation = null;
            if (finished && options.onDone) options.onDone();
        });
    }

    zoom(delta) {
        const mouseZoom = this.app.mapViewer.controlsManager.controls?.mouseZoom;
        if (mouseZoom) mouseZoom.deltaZoom += delta;
    }

    faceNorth() {
        const cm = this.app.mapViewer.controlsManager;
        const start = cm.rotation;
        animate(t => {
            cm.rotation = start * (1 - EasingFunctions.easeOutQuad(t));
        }, 300);
    }

    /**
     * Intersects the screen position with the rendered hires terrain.
     * Lowres tiles are flat meshes lifted in their shader, so a ray only finds their y = 0 plane;
     * without a hires hit the caller should fall back to {@link pickPlane} and treat the height as unknown.
     * @returns {Vector3|null}
     */
    pickGround(clientX, clientY) {
        const viewer = this.app.mapViewer, map = viewer.map;
        if (!map || !map.isLoaded) return null;
        const root = viewer.rootElement, offset = elementOffset(root);
        const normalized = new Vector2(
            ((clientX - offset.left) / root.clientWidth) * 2 - 1,
            -((clientY - offset.top) / root.clientHeight) * 2 + 1
        );
        viewer.camera.updateMatrixWorld();
        this.raycaster.setFromCamera(normalized, viewer.camera);

        const scene = map.hiresTileManager.scene;
        scene.position.set(0, 0, 0);
        scene.updateMatrixWorld();
        const hit = this.raycaster.intersectObject(scene, true).find(intersection => {
            for (let object = intersection.object; object; object = object.parent) if (!object.visible) return false;
            return true;
        });
        return hit ? hit.point.clone().add(this.raycaster.ray.direction.clone().multiplyScalar(0.05)) : null;
    }

    /**
     * Cheap cursor position: intersects with the horizontal plane at the camera target height.
     */
    pickPlane(clientX, clientY) {
        const viewer = this.app.mapViewer;
        const root = viewer.rootElement, offset = elementOffset(root);
        const normalized = new Vector2(
            ((clientX - offset.left) / root.clientWidth) * 2 - 1,
            -((clientY - offset.top) / root.clientHeight) * 2 + 1
        );
        viewer.camera.updateMatrixWorld();
        this.raycaster.setFromCamera(normalized, viewer.camera);
        const ray = this.raycaster.ray, y = viewer.controlsManager.position.y;
        if (Math.abs(ray.direction.y) < 1e-6) return null;
        const t = (y - ray.origin.y) / ray.direction.y;
        if (t < 0) return null;
        return new Vector3().copy(ray.origin).addScaledVector(ray.direction, t);
    }

    // ---------------------------------------------------------------- locations, measuring, highlight

    addressAt(x, z) {
        for (const provider of this.addressProviders) {
            try {
                const address = provider(x, z);
                if (address && address.label) return address;
            } catch (e) {
                console.error("Address provider failed", e);
            }
        }
        return null;
    }

    /**
     * @param info {{x: number, y: number|null, z: number, hires: boolean, light?: {sun: number, block: number}, tileUrls?: string[], pin?: boolean}}
     * @returns {boolean} false if the position was used as a measured point instead (no pin)
     */
    showLocation(info) {
        if (this.state.measure) {
            this.addMeasurePoint(info);
            return false;
        }
        this.closeContextMenu();
        const {pin, ...location} = info;
        this.state.card = {type: "location", ...location};
        // a phone's full-height sheet would hide the card
        this.state.sheetExpanded = false;
        if (pin) this.placePin(location);
        return true;
    }

    /**
     * Moves the clicked-position pin, e.g. for a searched or context-menu position.
     * Without a known height the pin sits at the camera target's height.
     * @param position {{x: number, y?: number|null, z: number}}
     */
    placePin(position) {
        const marker = this.app.popupMarker;
        if (!marker) return;
        const y = position.y === undefined || position.y === null ? Math.floor(this.app.mapViewer.controlsManager.position.y) : position.y;
        marker.position.set(Math.floor(position.x), y, Math.floor(position.z));
        marker.open();
        this.app.mapViewer.redraw();
    }

    /** Saved-place key of a plain map position. */
    coordinateKey(position) {
        const map = this.app.mapViewer.map;
        const y = position.y === undefined || position.y === null ? "" : position.y;
        return `coord:${map ? map.data.id : ""}:${position.x}:${y}:${position.z}`;
    }

    closeCard() {
        if (this.state.card && this.state.card.type === "measure") this.clearMeasure();
        this.state.card = null;
        if (this.app.popupMarker) this.app.popupMarker.close();
    }

    startMeasure(start = null) {
        this.state.measure = {points: []};
        this.state.card = {type: "measure"};
        if (this.app.popupMarker) this.app.popupMarker.close();
        if (start) this.addMeasurePoint(start);
        else this.updateMeasureOverlay();
    }

    addMeasurePoint(point) {
        if (!this.state.measure) return;
        this.state.measure.points.push({x: point.x, y: point.y === null || point.y === undefined ? this.app.mapViewer.controlsManager.position.y : point.y, z: point.z});
        this.state.card = {type: "measure"};
        this.updateMeasureOverlay();
    }

    undoMeasurePoint() {
        if (!this.state.measure) return;
        this.state.measure.points.pop();
        this.updateMeasureOverlay();
    }

    resetMeasure() {
        if (!this.state.measure) return;
        this.state.measure.points.splice(0);
        this.updateMeasureOverlay();
    }

    clearMeasure() {
        this.state.measure = null;
        this.updateMeasureOverlay();
    }

    measureLength() {
        const points = this.state.measure ? this.state.measure.points : [];
        let length = 0;
        for (let i = 1; i < points.length; i++) length += horizontalDistance(points[i - 1], points[i]);
        return length;
    }

    updateMeasureOverlay() {
        if (!this.overlay) return;
        const points = this.state.measure ? this.state.measure.points : [];
        const ids = new Set();
        if (points.length > 1) {
            ids.add("measure-line");
            this.overlay.updateMarkerFromData("measure-line", {
                type: "line",
                position: {...points[0]},
                line: points.map(p => ({x: p.x, y: p.y + 0.6, z: p.z})),
                lineWidth: 4,
                lineColor: {r: 26, g: 115, b: 232, a: 1},
                depthTest: false,
                listed: false,
            });
        }
        points.forEach((p, i) => {
            const id = `measure-point-${i}`;
            ids.add(id);
            const last = i === points.length - 1 && points.length > 1;
            this.overlay.updateMarkerFromData(id, {
                type: "html",
                position: {x: p.x, y: p.y + 0.6, z: p.z},
                html: `<div class="bm-ui-measure-point"></div>${last ? `<div class="bm-ui-measure-label">${this.formatBlocks(this.measureLength())}</div>` : ""}`,
                classes: ["bm-ui-measure"],
                listed: false,
            });
        });
        this.removeOverlayMarkers(id => id.startsWith("measure-") && !ids.has(id));
        this.app.mapViewer.rootElement.classList.toggle("bm-ui-measuring", !!this.state.measure);
        this.app.mapViewer.redraw();
    }

    /**
     * Highlights a polygon (e.g. a selected region) until it is cleared or the panel closes.
     * @param points {Array<{x: number, z: number}>}
     * @param y {number}
     * @param holes {Array<Array<{x: number, z: number}>>}
     */
    highlightShape(points, y = 64, holes = []) {
        if (!this.overlay || !points || points.length < 3) return;
        this.overlay.updateMarkerFromData("highlight", {
            type: "shape",
            position: {x: points[0].x, y, z: points[0].z},
            shape: points,
            holes,
            shapeY: y,
            lineWidth: 4,
            lineColor: {r: 26, g: 115, b: 232, a: 1},
            fillColor: {r: 26, g: 115, b: 232, a: 0.14},
            depthTest: false,
            listed: false,
        });
        this.app.mapViewer.redraw();
    }

    clearHighlight() {
        this.removeOverlayMarkers(id => id === "highlight");
    }

    removeOverlayMarkers(predicate) {
        if (!this.overlay) return;
        for (const [id, marker] of [...this.overlay.markers]) {
            if (predicate(id)) this.overlay.remove(marker);
        }
    }

    // ---------------------------------------------------------------- context menu

    openContextMenu(clientX, clientY) {
        const ground = this.pickGround(clientX, clientY);
        const point = ground || this.pickPlane(clientX, clientY);
        if (!point) return;
        const root = this.app.mapViewer.rootElement, offset = elementOffset(root);
        this.state.contextMenu = {
            left: clientX - offset.left,
            top: clientY - offset.top,
            point: {x: Math.floor(point.x), y: ground ? Math.floor(point.y) : null, z: Math.floor(point.z)},
        };
    }

    closeContextMenu() {
        this.state.contextMenu = null;
    }

    // ---------------------------------------------------------------- saving & sharing

    loadSaved() {
        try {
            const saved = getLocalStorage(SAVED_STORAGE_KEY);
            if (Array.isArray(saved)) this.state.saved = saved.filter(entry => entry && typeof entry.key === "string");
        } catch (e) {
            this.state.saved = [];
        }
    }

    persistSaved() {
        try {
            setLocalStorage(SAVED_STORAGE_KEY, this.state.saved);
        } catch (e) {
            // storage can be unavailable (private mode); the list then lasts for this visit
        }
    }

    isSaved(key) {
        return !!key && this.state.saved.some(entry => entry.key === key);
    }

    /**
     * @param place {{key: string, title: string, kind?: string, position: {x: number, y?: number, z: number}}}
     */
    toggleSaved(place) {
        if (!place || !place.key) return;
        const index = this.state.saved.findIndex(entry => entry.key === place.key);
        if (index >= 0) {
            this.state.saved.splice(index, 1);
            this.toast(i18n.t("ui.saved.removed"));
        } else {
            const position = place.position || {x: 0, z: 0};
            this.state.saved.unshift({
                key: place.key,
                title: place.title,
                kind: place.kind || "",
                mapId: this.app.mapViewer.map ? this.app.mapViewer.map.data.id : null,
                x: Math.round(position.x), y: position.y !== undefined && position.y !== null ? Math.round(position.y) : null, z: Math.round(position.z),
                savedAt: Date.now(),
            });
            this.toast(i18n.t("ui.saved.added"));
        }
        this.persistSaved();
    }

    removeSaved(key) {
        const index = this.state.saved.findIndex(entry => entry.key === key);
        if (index >= 0) this.state.saved.splice(index, 1);
        this.persistSaved();
    }

    async openSaved(entry) {
        if (entry.mapId && (!this.app.mapViewer.map || this.app.mapViewer.map.data.id !== entry.mapId)) {
            try {
                await this.app.switchMap(entry.mapId, false);
            } catch (e) {
                this.toast(i18n.t("ui.saved.mapMissing"));
                return;
            }
        }
        const position = {x: entry.x, y: entry.y, z: entry.z};
        const prefix = entry.key.split(":")[0];
        const resolver = this.resolvers.get(prefix);
        if (resolver && resolver(entry.key, entry)) return;
        if (prefix === "marker" && this.openMarkerByKey(entry.key)) {
            this.flyTo(position, {distance: Math.min(this.app.mapViewer.controlsManager.distance, 200)});
            return;
        }
        if (prefix === "player") {
            const data = this.playerMarkerData(entry.key.substring("player:".length));
            if (data) {
                this.openPlayer(data, true);
                this.focusPlayer(data);
                return;
            }
        }
        this.openPlace({key: entry.key, kind: entry.kind, title: entry.title, position, push: true, rows: () => this.positionRows(position)});
        this.flyTo(position, {distance: Math.min(this.app.mapViewer.controlsManager.distance, 200)});
    }

    openMarkerByKey(key) {
        const [, setId, ...rest] = key.split(":");
        const markerId = rest.join(":");
        let found = null;
        const visit = set => {
            if (found) return;
            if (set.data.id === setId && set.markers.has(markerId)) found = set.markers.get(markerId);
            set.markerSets.forEach(visit);
        };
        visit(this.app.mapViewer.markers);
        if (found) this.openMarker(found, true);
        return !!found;
    }

    /**
     * @param target {{title: string, x: number, y?: number, z: number}}
     */
    openShare(target) {
        this.closeContextMenu();
        this.state.share = {...target};
    }

    closeShare() {
        this.state.share = null;
    }

    /**
     * Builds a BlueMap address (same 10-part hash as the page address) centered on the position.
     */
    shareLink(position, includeView = true) {
        const map = this.app.mapViewer.map, cm = this.app.mapViewer.controlsManager;
        const base = location.href.split("#")[0];
        if (!map) return base;
        const y = position.y !== undefined && position.y !== null ? position.y : cm.position.y;
        const values = includeView ?
            [map.data.id, position.x, y, position.z, cm.distance, cm.rotation, cm.angle, cm.tilt, cm.ortho, this.app.appState.controls.state === "free" ? "perspective" : this.app.appState.controls.state] :
            [map.data.id, position.x, y, position.z, 150, 0, 0, 0, 0, "perspective"];
        return base + "#" + values.map((value, i) => i === 0 || i === 9 ? value : Math.round(value * (i >= 5 && i <= 7 ? 100 : 1)) / (i >= 5 && i <= 7 ? 100 : 1)).join(":");
    }

    // ---------------------------------------------------------------- utilities

    toast(text) {
        this.state.toast = text;
        clearTimeout(this.toastTimeout);
        this.toastTimeout = setTimeout(() => this.state.toast = null, 2800);
    }

    async copy(text, message = null) {
        let ok = false;
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(text);
                ok = true;
            }
        } catch (e) {
            ok = false;
        }
        if (!ok) {
            try {
                const area = document.createElement("textarea");
                area.value = text;
                area.setAttribute("readonly", "");
                area.style.position = "fixed";
                area.style.opacity = "0";
                document.body.appendChild(area);
                area.select();
                ok = document.execCommand("copy");
                area.remove();
            } catch (e) {
                ok = false;
            }
        }
        this.toast(ok ? `${message || i18n.t("ui.copied")}: ${text}` : `${i18n.t("ui.copyFailed")}: ${text}`);
        return ok;
    }

    formatCoordinates(position) {
        if (!position) return "";
        const parts = [`X ${Math.floor(position.x)}`];
        if (position.y !== undefined && position.y !== null) parts.push(`Y ${Math.floor(position.y)}`);
        parts.push(`Z ${Math.floor(position.z)}`);
        return parts.join(" · ");
    }

    copyCoordinates(position) {
        if (!position) return;
        const values = [position.x];
        if (position.y !== undefined && position.y !== null) values.push(position.y);
        values.push(position.z);
        return this.copy(values.map(value => Math.floor(value)).join(", "), i18n.t("ui.place.coordinatesCopied"));
    }

    formatBlocks(value) {
        return i18n.t("ui.blocks", {count: Math.round(value).toLocaleString(i18n.locale.value)});
    }

    isMobile() {
        return this.app.mapViewer.rootElement.clientWidth <= MOBILE_BREAK;
    }

}
