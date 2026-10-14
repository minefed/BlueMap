<template>
  <div v-if="card" class="bm-ui-card" role="dialog" :aria-label="card.type === 'measure' ? $t('ui.measure.title') : $t('ui.location.title')">
    <div class="bm-ui-card-main" v-if="card.type === 'location'">
      <div class="bm-ui-card-title">{{ address ? address.label : mapName }}</div>
      <div class="bm-ui-card-sub mono">{{ ui.formatCoordinates(card) }}</div>
      <div class="bm-ui-card-actions">
        <button class="bm-ui-mini-btn" @click="ui.copyCoordinates(card)"><Icon name="copy" :size="18"/>{{ $t("ui.place.copyCoordinates") }}</button>
        <button class="bm-ui-mini-btn" @click="ui.startMeasure(card)"><Icon name="ruler" :size="18"/>{{ $t("ui.measure.button") }}</button>
        <button class="bm-ui-mini-btn" :class="{on: saved}" @click="toggleSave"><Icon :name="saved ? 'bookmark' : 'bookmarkOutline'" :size="18"/>{{ saved ? $t("ui.saved.saved") : $t("ui.saved.save") }}</button>
        <button class="bm-ui-mini-btn" @click="ui.openShare({title: placeTitle, x: card.x, y: card.y, z: card.z})"><Icon name="share" :size="18"/>{{ $t("ui.share.button") }}</button>
        <button v-if="address && address.open" class="bm-ui-mini-btn" @click="address.open()"><Icon name="region" :size="18"/>{{ $t("ui.location.regionInfo") }}</button>
        <button class="bm-ui-mini-btn" :class="{on: details}" @click="details = !details"><Icon name="block" :size="18"/>{{ $t("ui.location.blockInfo") }}</button>
      </div>
      <dl v-if="details" class="bm-ui-card-details">
        <dt>{{ $t("blockTooltip.chunk") }}</dt><dd>{{ chunk }}</dd>
        <dt>{{ $t("blockTooltip.region.file") }}</dt><dd>{{ regionFile }}</dd>
        <template v-if="card.light">
          <dt>{{ $t("blockTooltip.light.light") }}</dt><dd>{{ $t("blockTooltip.light.sun") }} {{ card.light.sun }} · {{ $t("blockTooltip.light.block") }} {{ card.light.block }}</dd>
        </template>
        <template v-if="!card.hires">
          <dt>{{ $t("ui.location.precision") }}</dt><dd>{{ $t("ui.location.lowres") }}</dd>
        </template>
        <template v-if="appState.debug && card.tileUrls && card.tileUrls.length">
          <dt>{{ $t("ui.location.tiles") }}</dt><dd><div v-for="url of card.tileUrls" :key="url">{{ url }}</div></dd>
        </template>
      </dl>
    </div>

    <div class="bm-ui-card-main" v-else-if="card.type === 'measure'">
      <div class="bm-ui-card-title">{{ $t("ui.measure.title") }}</div>
      <div class="bm-ui-card-big">{{ ui.formatBlocks(length) }}</div>
      <div class="bm-ui-card-sub">{{ points < 2 ? $t("ui.measure.hint") : $t("ui.measure.summary", {count: points, minutes: walkMinutes}) }}</div>
      <div class="bm-ui-card-actions">
        <button class="bm-ui-mini-btn" :disabled="!points" @click="ui.undoMeasurePoint()">{{ $t("ui.measure.undo") }}</button>
        <button class="bm-ui-mini-btn" :disabled="!points" @click="ui.resetMeasure()">{{ $t("ui.measure.reset") }}</button>
      </div>
    </div>

    <button class="bm-ui-icon-btn" @click="ui.closeCard()" :aria-label="$t('ui.close')"><Icon name="close"/></button>
  </div>
</template>

<script>
import Icon from "./Icon.vue";

// Minecraft walking speed in blocks per second
const WALK_SPEED = 4.317;

export default {
  name: "LocationCard",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      appState: this.$bluemap.appState,
      viewer: this.$bluemap.mapViewer.data,
      details: false,
    }
  },
  computed: {
    card() {
      return this.ui.state.card;
    },
    mapName() {
      return this.viewer.map ? this.viewer.map.name : "";
    },
    address() {
      this.ui.state.revision;
      return this.card && this.card.type === "location" ? this.ui.addressAt(this.card.x, this.card.z) : null;
    },
    key() {
      this.viewer.map;
      return this.card && this.card.type === "location" ? this.ui.coordinateKey(this.card) : null;
    },
    placeTitle() {
      const c = this.card;
      return `${this.address ? this.address.label : this.mapName} (${c.x}, ${c.z})`;
    },
    saved() {
      this.ui.state.saved.length;
      return this.ui.isSaved(this.key);
    },
    chunk() {
      const c = this.card;
      return `${Math.floor(c.x / 16)}, ${Math.floor(c.z / 16)}`;
    },
    regionFile() {
      const c = this.card;
      return `r.${Math.floor(c.x / 512)}.${Math.floor(c.z / 512)}.mca`;
    },
    points() {
      return this.ui.state.measure ? this.ui.state.measure.points.length : 0;
    },
    length() {
      this.points;
      return this.ui.measureLength();
    },
    walkMinutes() {
      return Math.max(1, Math.round(this.length / WALK_SPEED / 60));
    }
  },
  watch: {
    key() {
      this.details = false;
    }
  },
  methods: {
    toggleSave() {
      const c = this.card;
      this.ui.toggleSaved({key: this.key, title: this.placeTitle, kind: this.$t("ui.location.coordinates"), position: {x: c.x, y: c.y, z: c.z}});
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-card {
  position: absolute;
  left: 50%;
  bottom: 32px;
  transform: translateX(-50%);
  z-index: 18;
  width: min(440px, calc(100% - 32px));
  background: var(--ui-surface);
  color: var(--ui-text);
  border-radius: 12px;
  box-shadow: var(--ui-shadow);
  padding: 12px 8px 12px 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  pointer-events: auto;
  box-sizing: border-box;

  .bm-ui-panel-open & {
    left: calc(50% + #{$ui-panel-width} / 2);
    width: min(440px, calc(100% - #{$ui-panel-width} - 32px));
  }

  @media (max-width: $ui-mobile-break) {
    &, .bm-ui-panel-open & {
      left: 12px;
      right: 12px;
      width: auto;
      transform: none;
      bottom: calc(30px + env(safe-area-inset-bottom, 0px));
    }
  }
}

.bm-ui-card-main {
  flex: 1;
  min-width: 0;
}

.bm-ui-card-title {
  font-size: 15px;
  font-weight: 500;
}

.bm-ui-card-sub {
  font-size: 13px;
  color: var(--ui-text-2);
  margin-top: 2px;

  &.mono {
    font-family: var(--ui-font-mono);
  }
}

.bm-ui-card-big {
  font-size: 20px;
  margin-top: 4px;
  font-variant-numeric: tabular-nums;
}

.bm-ui-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;

  .bm-ui-mini-btn:disabled {
    opacity: .5;
    cursor: default;
  }
}

.bm-ui-card-details {
  margin: 10px 0 0;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 16px;
  font-size: 12px;

  dt {
    color: var(--ui-text-2);
  }

  dd {
    margin: 0;
    font-family: var(--ui-font-mono);
    overflow-wrap: anywhere;
  }
}

.bm-ui-measure {
  position: relative;
  pointer-events: none;

  .bm-ui-measure-point {
    position: absolute;
    left: -6px;
    top: -6px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #fff;
    border: 2.5px solid #1a73e8;
  }

  .bm-ui-measure-label {
    position: absolute;
    left: 10px;
    top: -26px;
    white-space: nowrap;
    padding: 2px 8px;
    border-radius: 11px;
    background: #fff;
    color: #202124;
    font: 500 12px var(--ui-font);
    box-shadow: 0 1px 3px rgba(0, 0, 0, .3);
  }
}
</style>
