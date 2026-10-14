<template>
  <div class="bm-ui-map-ctrl">
    <button class="bm-ui-ctrl-btn" @click="ui.faceNorth()" :title="$t('compass.tooltip')" :aria-label="$t('compass.tooltip')">
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" :style="{transform: `rotate(${-controls.rotation}rad)`}">
        <path d="M12 3 8.5 12h7z" fill="#ea4335"/><path d="M12 21l3.5-9h-7z" fill="currentColor"/>
      </svg>
    </button>
    <button v-if="appState.controls.state !== 'free'" class="bm-ui-ctrl-btn bm-ui-ctrl-text" :class="{on: appState.controls.state === 'perspective'}"
            @click="toggleView" :title="$t('ui.controls.toggleView')" :aria-label="$t('ui.controls.toggleView')">
      {{ appState.controls.state === "flat" ? "2D" : "3D" }}
    </button>
    <button class="bm-ui-ctrl-btn" @click="$bluemap.resetCamera()" :title="$t('resetCamera.tooltip')" :aria-label="$t('resetCamera.tooltip')"><Icon name="target"/></button>
    <div class="bm-ui-zoom" v-if="appState.controls.showZoomButtons && appState.controls.state !== 'free'">
      <button class="bm-ui-ctrl-btn" @click="ui.zoom(-1)" :aria-label="$t('ui.controls.zoomIn')" :title="$t('ui.controls.zoomIn')"><Icon name="add"/></button>
      <hr>
      <button class="bm-ui-ctrl-btn" @click="ui.zoom(1)" :aria-label="$t('ui.controls.zoomOut')" :title="$t('ui.controls.zoomOut')"><Icon name="remove"/></button>
    </div>
  </div>
</template>

<script>
import Icon from "./Icon.vue";

export default {
  name: "MapButtons",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      appState: this.$bluemap.appState,
      controls: this.$bluemap.mapViewer.controlsManager.data,
    }
  },
  methods: {
    toggleView() {
      if (this.appState.controls.state === "flat") this.$bluemap.setPerspectiveView(500);
      else this.$bluemap.setFlatView(500);
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-map-ctrl {
  position: absolute;
  right: 16px;
  bottom: 36px;
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  pointer-events: auto;

  @media (max-width: $ui-mobile-break) {
    right: 12px;
    bottom: calc(30px + env(safe-area-inset-bottom, 0px));

    .bm-ui-panel-open & {
      bottom: calc(52% + 12px);
    }

    .bm-ui-sheet-full & {
      display: none;
    }

    .bm-ui-zoom {
      display: none;
    }
  }
}

.bm-ui-ctrl-btn {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: 0;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  color: var(--ui-text-2);
  display: grid;
  place-items: center;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: var(--ui-text);
    background: var(--ui-surface-2);
  }

  &.on {
    color: var(--ui-accent-text);
  }

  &.bm-ui-ctrl-text {
    font: 700 13px var(--ui-font);
  }
}

.bm-ui-zoom {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--ui-shadow-sm);
  background: var(--ui-surface);

  .bm-ui-ctrl-btn {
    box-shadow: none;
    border-radius: 0;
  }

  hr {
    margin: 0 6px;
    border: 0;
    border-top: 1px solid var(--ui-line);
  }
}
</style>
