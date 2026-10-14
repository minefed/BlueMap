<template>
  <div class="bm-ui-layers-dock">
    <button class="bm-ui-layers-btn" @click="ui.state.layersOpen = !ui.state.layersOpen" :aria-expanded="String(ui.state.layersOpen)" :aria-label="$t('ui.layers.title')">
      <MapPreview v-if="viewer.map" class="bm-ui-layers-thumb" :x="thumbX" :z="thumbZ" :scale="8"/>
      <span><Icon name="layers" :size="14"/><b>{{ $t("ui.layers.button") }}</b></span>
    </button>
    <div class="bm-ui-layers-quick" v-if="tiles.length && !ui.state.layersOpen">
      <button v-for="tile of tiles.slice(0, 5)" :key="tile.id" class="bm-ui-lq" :class="{on: tile.on}" @click="toggle(tile)">
        <span class="bm-ui-lq-sq"><Icon :name="tile.icon" :size="20"/></span>{{ tile.label }}
      </button>
      <button class="bm-ui-lq" @click="ui.state.layersOpen = true">
        <span class="bm-ui-lq-sq"><Icon name="tune" :size="20"/></span>{{ $t("ui.layers.more") }}
      </button>
    </div>
  </div>

  <div v-if="ui.state.layersOpen" class="bm-ui-layers-pop" role="dialog" :aria-label="$t('ui.layers.title')">
    <div class="bm-ui-lp-head">
      <h2>{{ $t("ui.layers.title") }}</h2>
      <button class="bm-ui-icon-btn" @click="ui.state.layersOpen = false" :aria-label="$t('ui.close')"><Icon name="close"/></button>
    </div>

    <section class="bm-ui-lp-sect" v-if="maps.length > 1">
      <h3>{{ $t("maps.title") }}</h3>
      <div class="bm-ui-tiles c3">
        <button v-for="map of maps" :key="map.data.id" class="bm-ui-tile" :class="{on: viewer.map && viewer.map.id === map.data.id}" @click="switchMap(map.data.id)">
          <span class="bm-ui-tile-sq"><MapPreview :map="map" :x="map.data.startPos.x" :z="map.data.startPos.z" :scale="6"/></span>{{ map.data.name }}
        </button>
      </div>
    </section>

    <section class="bm-ui-lp-sect">
      <h3>{{ $t("controls.title") }}</h3>
      <div class="bm-ui-tiles c3">
        <button class="bm-ui-tile" :class="{on: appState.controls.state === 'perspective'}" @click="setView('perspective')"><span class="bm-ui-tile-sq"><Icon name="cube"/></span>{{ $t("controls.perspective.button") }}</button>
        <button class="bm-ui-tile" :class="{on: appState.controls.state === 'flat'}" @click="setView('flat')"><span class="bm-ui-tile-sq"><Icon name="square"/></span>{{ $t("controls.flatView.button") }}</button>
        <button v-if="appState.controls.enableFreeFlight" class="bm-ui-tile" :class="{on: appState.controls.state === 'free'}" @click="setView('free')"><span class="bm-ui-tile-sq"><Icon name="flight"/></span>{{ $t("controls.freeFlight.button") }}</button>
      </div>
    </section>

    <section class="bm-ui-lp-sect" v-if="tiles.length">
      <h3>{{ $t("ui.layers.details") }}</h3>
      <div class="bm-ui-tiles">
        <button v-for="tile of tiles" :key="tile.id" class="bm-ui-tile" :class="{on: tile.on}" @click="toggle(tile)" :aria-pressed="String(tile.on)">
          <span class="bm-ui-tile-sq"><Icon :name="tile.icon"/></span>{{ tile.label }}
        </button>
      </div>
    </section>

    <section class="bm-ui-lp-sect">
      <h3>{{ $t("lighting.title") }}</h3>
      <div class="bm-ui-tiles c3">
        <button class="bm-ui-tile" :class="{on: isDay}" @click="setDay(true)"><span class="bm-ui-tile-sq"><Icon name="sun"/></span>{{ $t("ui.layers.day") }}</button>
        <button class="bm-ui-tile" :class="{on: !isDay}" @click="setDay(false)"><span class="bm-ui-tile-sq"><Icon name="moon"/></span>{{ $t("ui.layers.night") }}</button>
      </div>
    </section>

    <section class="bm-ui-lp-status" v-if="statuses.length">
      <div v-for="status of statuses" :key="status.id" class="bm-ui-status-line" :data-state="status.state">{{ status.text }}</div>
    </section>
  </div>
</template>

<script>
import Icon from "./Icon.vue";
import MapPreview from "./MapPreview.vue";
import {plainLabel} from "../../js/ui/MapUi";
import {animate, EasingFunctions} from "../../js/util/Utils";

let lightAnimation = null;

export default {
  name: "LayersControl",
  components: {Icon, MapPreview},
  data() {
    return {
      ui: this.$bluemap.ui,
      appState: this.$bluemap.appState,
      viewer: this.$bluemap.mapViewer.data,
      controls: this.$bluemap.mapViewer.controlsManager.data,
      markers: this.$bluemap.mapViewer.markers.data,
    }
  },
  computed: {
    maps() {
      this.appState.maps.length;
      return this.$bluemap.maps;
    },
    thumbX() {
      return Math.round(this.controls.position.x / 64) * 64;
    },
    thumbZ() {
      return Math.round(this.controls.position.z / 64) * 64;
    },
    isDay() {
      return this.viewer.uniforms.sunlightStrength.value > 0.6;
    },
    tiles() {
      this.ui.state.tick;
      this.markers.markerSets.length;
      const root = this.$bluemap.mapViewer.markers, tiles = [];
      const redraw = () => this.$bluemap.mapViewer.redraw();
      for (const set of root.markerSets.values()) {
        const players = set.data.id === "bm-players";
        if (!players && (set.data.id.startsWith("bm-") || !set.data.toggleable)) continue;
        if (players && set.data.markers.length === 0 && set.data.markerSets.length === 0) continue;
        tiles.push({
          id: `set:${set.data.id}`, order: players ? 30 : 60,
          label: players ? this.$t("players.title") : plainLabel(set.data.label, set.data.id),
          icon: players ? "person" : "layers",
          on: set.visible,
          set: value => { set.visible = value; set.data.saveState(); redraw(); },
        });
      }
      for (const layer of this.ui.layers) {
        tiles.push({
          id: `layer:${layer.id}`, order: layer.order ?? 50,
          label: typeof layer.label === "function" ? layer.label() : layer.label,
          icon: layer.icon || "layers",
          on: !!layer.get(),
          set: value => { layer.set(value); redraw(); this.ui.refresh(); },
        });
      }
      tiles.push({
        id: "chunk-borders", order: 100, label: this.$t("ui.layers.chunkBorders"), icon: "grid",
        on: !!this.viewer.uniforms.chunkBorders.value,
        set: value => { this.$bluemap.setChunkBorders(value); this.$bluemap.saveUserSettings(); redraw(); },
      });
      return tiles.sort((a, b) => a.order - b.order);
    },
    statuses() {
      return Object.entries(this.ui.state.statuses).map(([id, status]) => ({id, ...status}));
    }
  },
  methods: {
    toggle(tile) {
      tile.set(!tile.on);
    },
    async switchMap(id) {
      this.ui.closePanel();
      this.ui.closeCard();
      await this.$bluemap.switchMap(id);
    },
    setView(mode) {
      const fromFree = this.appState.controls.state === "free" ? 100 : 0;
      if (mode === "perspective") this.$bluemap.setPerspectiveView(500, fromFree);
      else if (mode === "flat") this.$bluemap.setFlatView(500, fromFree);
      else this.$bluemap.setFreeFlight(500);
    },
    setDay(day) {
      if (lightAnimation) lightAnimation.cancel();
      const uniform = this.viewer.uniforms.sunlightStrength, start = uniform.value, target = day ? 1 : 0.25;
      lightAnimation = animate(t => {
        const u = EasingFunctions.easeOutQuad(t);
        uniform.value = start * (1 - u) + target * u;
        this.$bluemap.mapViewer.redraw();
      }, 300);
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-layers-dock {
  position: absolute;
  left: 16px;
  bottom: 28px;
  z-index: 15;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  pointer-events: auto;

  .bm-ui-panel-open & {
    left: $ui-panel-width + 16px;
  }

  &:hover .bm-ui-layers-quick, &:focus-within .bm-ui-layers-quick {
    display: flex;
  }

  @media (max-width: $ui-mobile-break) {
    &, .bm-ui-panel-open & {
      left: auto;
      right: 12px;
      bottom: auto;
      top: calc(160px + env(safe-area-inset-top, 0px));
    }

    &:hover .bm-ui-layers-quick, &:focus-within .bm-ui-layers-quick {
      display: none;
    }
  }
}

.bm-ui-layers-btn {
  position: relative;
  width: 72px;
  height: 72px;
  padding: 0;
  border-radius: 8px;
  border: 2px solid var(--ui-surface);
  box-shadow: var(--ui-shadow);
  cursor: pointer;
  overflow: hidden;
  background: var(--ui-surface-2);

  > span {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
    padding: 4px 0 5px;
    font: 500 11px var(--ui-font);
    color: #fff;
    background: linear-gradient(transparent, rgba(0, 0, 0, .65));
  }

  @media (max-width: $ui-mobile-break) {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 0;
    background: var(--ui-surface);
    box-shadow: var(--ui-shadow-sm);

    .bm-ui-layers-thumb, b {
      display: none;
    }

    > span {
      position: static;
      height: 100%;
      padding: 0;
      background: none;
      color: var(--ui-text-2);

      .bm-ui-icon {
        width: 22px;
        height: 22px;
      }
    }
  }
}

.bm-ui-layers-quick {
  display: none;
  gap: 2px;
  padding: 6px;
  background: var(--ui-surface);
  border-radius: 8px;
  box-shadow: var(--ui-shadow);
}

.bm-ui-lq {
  width: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  cursor: pointer;
  font: 400 11px var(--ui-font);
  color: var(--ui-text-2);
  padding: 4px 2px;
  border-radius: 6px;
  text-align: center;
  line-height: 1.25;

  &:hover {
    background: var(--ui-hover);
  }

  .bm-ui-lq-sq {
    width: 36px;
    height: 36px;
    border-radius: 6px;
    display: grid;
    place-items: center;
    background: var(--ui-surface-2);
    border: 2px solid transparent;
  }

  &.on {
    color: var(--ui-accent-text);

    .bm-ui-lq-sq {
      border-color: var(--ui-accent);
      color: var(--ui-accent-text);
      background: var(--ui-accent-weak);
    }
  }
}

.bm-ui-layers-pop {
  position: absolute;
  left: 16px;
  bottom: 112px;
  width: 352px;
  max-height: calc(100% - 140px);
  overflow: auto;
  z-index: 40;
  background: var(--ui-surface);
  color: var(--ui-text);
  border-radius: 12px;
  box-shadow: var(--ui-shadow);
  padding: 4px 0 8px;
  pointer-events: auto;

  .bm-ui-panel-open & {
    left: $ui-panel-width + 16px;
  }

  @media (max-width: $ui-mobile-break) {
    &, .bm-ui-panel-open & {
      left: 0;
      right: 0;
      bottom: 0;
      width: auto;
      border-radius: 16px 16px 0 0;
      max-height: 75%;
      padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
    }
  }
}

.bm-ui-lp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 8px 0 20px;

  h2 {
    margin: 0;
    padding: 0;
    width: auto;
    font-size: 16px;
    font-weight: 500;

    &:after {
      display: none;
    }
  }
}

.bm-ui-lp-sect {
  padding: 8px 20px 12px;

  & + & {
    border-top: 1px solid var(--ui-line);
  }

  h3 {
    margin: 4px 0 10px;
    padding: 0;
    font-size: 13px;
    font-weight: 500;
    color: var(--ui-text-2);
  }
}

.bm-ui-tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px 6px;

  &.c3 {
    grid-template-columns: repeat(3, 1fr);
  }
}

.bm-ui-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: 0;
  background: none;
  cursor: pointer;
  font: 400 12px var(--ui-font);
  color: var(--ui-text-2);
  padding: 0;
  text-align: center;
  line-height: 1.3;
  overflow-wrap: anywhere;

  .bm-ui-tile-sq {
    width: 56px;
    height: 56px;
    border-radius: 8px;
    display: grid;
    place-items: center;
    background: var(--ui-surface-2);
    color: var(--ui-text-2);
    border: 2px solid transparent;
    overflow: hidden;
  }

  &.on {
    color: var(--ui-accent-text);
    font-weight: 500;

    .bm-ui-tile-sq {
      border-color: var(--ui-accent);
      color: var(--ui-accent-text);
      background: var(--ui-accent-weak);
    }
  }
}

.bm-ui-lp-status {
  border-top: 1px solid var(--ui-line);
  padding: 10px 20px 4px;
}

.bm-ui-status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ui-text-2);
  min-height: 22px;

  &:before {
    content: "";
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--ui-text-3);
    flex: none;
  }

  &[data-state="live"]:before {
    background: #34a853;
  }

  &[data-state="offline"]:before {
    background: #e37400;
  }
}
</style>
