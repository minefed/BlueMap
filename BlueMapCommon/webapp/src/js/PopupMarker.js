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
import {Marker} from "./markers/Marker";
import {CSS2DObject} from "./util/CSS2DRenderer";
import {animate, htmlToElement} from "./util/Utils";
import {BoxGeometry, MeshBasicMaterial, Mesh} from "three";

const PIN_SVG = `<svg viewBox="0 0 24 34" aria-hidden="true"><path d="M12 1C5.9 1 1 5.9 1 12c0 8.3 11 21 11 21s11-12.7 11-21C23 5.9 18.1 1 12 1Z" fill="#ea4335" stroke="#a50e0e" stroke-width="1.2"/><circle cx="12" cy="12" r="4" fill="#a50e0e"/></svg>`;

/**
 * Marks the last clicked map position with a pin and hands the clicked block's data to the map UI.
 */
export class PopupMarker extends Marker {

    constructor(id, appState, events, onLocation = null) {
        super(id);

        this.data.type = "popup";
        this.data.label = "Last Map Interaction";
        this.data.listed = false;

        this.appState = appState;
        this.events = events;
        this.onLocation = onLocation;
        this.visible = false;

        this.elementObject = new CSS2DObject(htmlToElement(`<div id="bm-marker-${this.data.id}" class="bm-marker-${this.data.type}">${PIN_SVG}</div>`));
        this.elementObject.position.set(0.5, 1, 0.5);
        this.elementObject.disableDepthTest = true;
        this.addEventListener( 'removed', () => {
            if (this.element.parentNode) this.element.parentNode.removeChild(this.element);
        });

        let cubeGeo = new BoxGeometry(1.01, 1.01, 1.01).translate(0.5, 0.5, 0.5);
        let cubeMaterial = new MeshBasicMaterial( {color: 0xffffff, opacity: 0.5, transparent: true} );
        this.cube = new Mesh(cubeGeo, cubeMaterial);
        this.cube.onClick = evt => this.onClick(evt);

        this.add(this.elementObject);
        this.add(this.cube);

        this.animation = null;

        this.events.addEventListener('bluemapMapInteraction', evt => window.setTimeout(() => this.onMapInteraction(evt)));
    }

    onClick(event) {
        return true;
    }

    onMapInteraction = evt => {
        if (evt.detail.data && evt.detail.data.doubleTap) return;

        let isHires = true;
        let int = evt.detail.hiresHit;

        if (evt.detail.lowresHits) {
            for (let i = 0; i < evt.detail.lowresHits.length; i++) {
                if (!int) {
                    isHires = false;
                    int = evt.detail.lowresHits[i];
                } else break;
            }
        }

        if (!int) return;

        this.position
            .copy(int.pointOnLine || int.point)
            .add(evt.detail.ray.direction.clone().multiplyScalar(0.05))
            .floor();

        let light = null;
        if (isHires && int.object && int.object.geometry) {
            let attributes = int.object.geometry.attributes;
            if (attributes.sunlight && attributes.blocklight) {
                light = {
                    sun: attributes.sunlight.array[int.faceIndex * 3],
                    block: attributes.blocklight.array[int.faceIndex * 3]
                };
            }
        }

        let tileUrls = [];
        if (isHires && evt.detail.hiresHit?.object?.userData?.tileUrl) tileUrls.push(evt.detail.hiresHit.object.userData.tileUrl);
        if (evt.detail.lowresHits) {
            for (let i = 0; i < evt.detail.lowresHits.length; i++) {
                let url = evt.detail.lowresHits[i]?.object?.userData?.tileUrl;
                if (url) tileUrls.push(url);
            }
        }

        if (this.appState.debug){
            console.debug("Clicked Position Data:", evt.detail);
        }

        // the map UI may use the click for something else (e.g. a measured point) and hide the pin
        let showPin = true;
        if (this.onLocation) showPin = this.onLocation({
            x: this.position.x,
            y: isHires ? this.position.y : null,
            z: this.position.z,
            hires: isHires,
            light: light,
            tileUrls: tileUrls,
        }) !== false;

        if (showPin) this.open();
        else this.close();
    };

    open() {
        if (this.animation) this.animation.cancel();

        this.visible = true;
        this.cube.visible = true;

        this.element.style.opacity = "0";
        this.animation = animate(progress => {
            this.element.style.opacity = progress.toString();
        }, 200);
    }

    close() {
        if (this.animation) this.animation.cancel();
        if (!this.visible) return;

        this.cube.visible = false;

        let startOpacity = parseFloat(this.element.style.opacity) || 1;
        this.animation = animate(progress => {
            this.element.style.opacity = (startOpacity - progress * startOpacity).toString();
        }, 200, finished => {
            if (finished) this.visible = false;
        });
    }

    /**
     * @returns {Element}
     */
    get element() {
        return this.elementObject.element.getElementsByTagName("div")[0];
    }

    dispose() {
        super.dispose();

        if (this.element.parentNode) this.element.parentNode.removeChild(this.element);
    }

}
