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
import {pathFromCoords} from "../util/Utils";

const MAX_CACHED_TILES = 48;

/**
 * Draws flat top-down previews from the colour half of the already rendered lowres tiles.
 * No extra rendering is needed, so panel heroes and layer thumbnails stay cheap.
 */
export class TilePreview {

    /**
     * @param app {BlueMapApp}
     */
    constructor(app) {
        this.app = app;
        /** @type {Map<string, Promise<HTMLImageElement|null>>} */
        this.images = new Map();
    }

    /**
     * @param url {string}
     * @returns {Promise<HTMLImageElement|null>}
     */
    image(url) {
        if (this.images.has(url)) {
            const cached = this.images.get(url);
            this.images.delete(url);
            this.images.set(url, cached);
            return cached;
        }
        const promise = new Promise(resolve => {
            const img = new Image();
            img.decoding = "async";
            img.onload = () => resolve(img);
            img.onerror = () => resolve(null);
            img.src = url;
        });
        this.images.set(url, promise);
        while (this.images.size > MAX_CACHED_TILES) this.images.delete(this.images.keys().next().value);
        return promise;
    }

    /**
     * Renders the area around (x, z) into the canvas.
     * @param canvas {HTMLCanvasElement}
     * @param map {object} BlueMap map (with .data)
     * @param x {number}
     * @param z {number}
     * @param blocksPerPixel {number} preview scale in css pixels
     * @param background {string}
     * @returns {Promise<boolean>} whether any tile was drawn
     */
    async render(canvas, map, x, z, blocksPerPixel = 0.34, background = "#d6dbe1") {
        if (!canvas || !map || !map.data || !map.data.lowres) return false;
        const rect = canvas.getBoundingClientRect();
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        const width = Math.max(1, Math.round((rect.width || canvas.width) * ratio));
        const height = Math.max(1, Math.round((rect.height || canvas.height) * ratio));
        if (canvas.width !== width) canvas.width = width;
        if (canvas.height !== height) canvas.height = height;
        const ctx = canvas.getContext("2d");
        if (!ctx) return false;

        const lowres = map.data.lowres;
        const tileSize = lowres.tileSize.x;
        // choose the coarsest lod that still has at least one tile pixel per output pixel
        let lod = 1;
        while (lod < lowres.lodCount && Math.pow(lowres.lodFactor, lod) <= blocksPerPixel * 1.01) lod++;
        const scale = Math.pow(lowres.lodFactor, lod - 1);
        const tileWorld = tileSize * scale;

        const blocksPerDevicePixel = blocksPerPixel / ratio;
        const worldWidth = width * blocksPerDevicePixel, worldHeight = height * blocksPerDevicePixel;
        const x0 = x - worldWidth / 2, z0 = z - worldHeight / 2;

        const draws = [];
        for (let tx = Math.floor(x0 / tileWorld); tx <= Math.floor((x0 + worldWidth) / tileWorld); tx++) {
            for (let tz = Math.floor(z0 / tileWorld); tz <= Math.floor((z0 + worldHeight) / tileWorld); tz++) {
                const url = `${map.data.dataUrl}tiles/${lod}/${pathFromCoords(tx, tz)}.png?${this.app.mapViewer.tileCacheHash}`;
                draws.push(this.image(url).then(img => ({img, tx, tz})));
            }
        }
        const tiles = await Promise.all(draws);

        ctx.imageSmoothingEnabled = false;
        ctx.fillStyle = background;
        ctx.fillRect(0, 0, width, height);
        let drawn = false;
        for (const {img, tx, tz} of tiles) {
            if (!img) continue;
            // the upper half of a lowres tile holds colours, one pixel per (scaled) block
            const tileX0 = tx * tileWorld, tileZ0 = tz * tileWorld;
            const sx0 = Math.max(x0, tileX0), sz0 = Math.max(z0, tileZ0);
            const sx1 = Math.min(x0 + worldWidth, tileX0 + tileWorld), sz1 = Math.min(z0 + worldHeight, tileZ0 + tileWorld);
            if (sx1 <= sx0 || sz1 <= sz0) continue;
            ctx.drawImage(img,
                (sx0 - tileX0) / scale, (sz0 - tileZ0) / scale, (sx1 - sx0) / scale, (sz1 - sz0) / scale,
                (sx0 - x0) / blocksPerDevicePixel, (sz0 - z0) / blocksPerDevicePixel,
                (sx1 - sx0) / blocksPerDevicePixel, (sz1 - sz0) / blocksPerDevicePixel);
            drawn = true;
        }
        return drawn;
    }

}
