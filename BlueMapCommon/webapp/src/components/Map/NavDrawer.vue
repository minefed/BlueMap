<template>
  <div v-if="open" class="bm-ui-scrim" @click="close"></div>
  <nav class="bm-ui-drawer" :class="{open}" :aria-hidden="String(!open)" :aria-label="$t('menu.title')" @keydown.esc="close">
    <div class="bm-ui-dr-head">
      <img :src="'assets/logoCircle64.png'" alt="" class="bm-ui-dr-logo">
      <div class="bm-ui-dr-title"><b>{{ title }}</b><small>BlueMap {{ $bluemap.settings ? $bluemap.settings.version : "" }}</small></div>
      <button class="bm-ui-icon-btn" @click="close" :aria-label="$t('ui.close')" :tabindex="open ? 0 : -1"><Icon name="close"/></button>
    </div>

    <div class="bm-ui-dr-sect" v-if="maps.length > 1">
      <h4>{{ $t("maps.title") }}</h4>
      <button v-for="map of maps" :key="map.id" class="bm-ui-dr-item" :class="{on: viewer.map && viewer.map.id === map.id}" :tabindex="open ? 0 : -1" @click="switchMap(map.id)">
        <Icon name="globe"/>{{ map.name }}
      </button>
    </div>

    <div class="bm-ui-dr-sect">
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.openList(savedList(ui)))"><Icon name="bookmark"/>{{ $t("ui.saved.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.startMeasure())"><Icon name="ruler"/>{{ $t("ui.measure.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.state.layersOpen = true)"><Icon name="layers"/>{{ $t("ui.layers.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.openList(markerSetList(ui)))"><Icon name="flag"/>{{ $t("markers.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.openList(playersList(ui)))"><Icon name="group"/>{{ $t("players.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.openSettings())"><Icon name="settings"/>{{ $t("settings.title") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => ui.openHelp())"><Icon name="info"/>{{ $t("info.title") }}</button>
    </div>

    <div class="bm-ui-dr-sect">
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(fullscreen)"><Icon name="fullscreen"/>{{ $t("goFullscreen.button") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => $bluemap.takeScreenshot())"><Icon name="camera"/>{{ $t("screenshot.button") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" @click="run(() => $bluemap.resetCamera())"><Icon name="target"/>{{ $t("resetCamera.button") }}</button>
      <button class="bm-ui-dr-item" :tabindex="open ? 0 : -1" :title="$t('updateMap.tooltip')" @click="run(() => $bluemap.updateMap())"><Icon name="refresh"/>{{ $t("updateMap.button") }}</button>
    </div>
  </nav>
</template>

<script>
import Icon from "./Icon.vue";
import {markerSetList, playersList, savedList} from "../../js/ui/lists";

export default {
  name: "NavDrawer",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      viewer: this.$bluemap.mapViewer.data,
      appState: this.$bluemap.appState,
    }
  },
  computed: {
    open() {
      return this.ui.state.drawerOpen;
    },
    maps() {
      return this.appState.maps;
    },
    title() {
      return this.viewer.map ? this.viewer.map.name : "BlueMap";
    }
  },
  methods: {
    savedList, markerSetList, playersList,
    close() {
      this.ui.state.drawerOpen = false;
    },
    run(action) {
      this.close();
      action();
    },
    async switchMap(id) {
      this.close();
      this.ui.closePanel();
      this.ui.closeCard();
      await this.$bluemap.switchMap(id);
    },
    fullscreen() {
      const request = document.fullscreenElement ? document.exitFullscreen() : document.body.requestFullscreen();
      if (request && request.catch) request.catch(() => this.ui.toast(this.$t("ui.fullscreenUnavailable")));
    }
  }
}
</script>

<style lang="scss">
.bm-ui-drawer {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 320px;
  max-width: 86%;
  z-index: 51;
  background: var(--ui-surface);
  color: var(--ui-text);
  box-shadow: var(--ui-shadow);
  transform: translateX(-105%);
  transition: transform .2s ease;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  visibility: hidden;

  &.open {
    transform: none;
    visibility: visible;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}

.bm-ui-dr-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: calc(16px + env(safe-area-inset-top, 0px)) 12px 16px 20px;
  border-bottom: 1px solid var(--ui-line);
}

.bm-ui-dr-logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.bm-ui-dr-title {
  flex: 1;
  min-width: 0;

  b {
    display: block;
    font-size: 16px;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    font-size: 12px;
    color: var(--ui-text-2);
    font-family: var(--ui-font-mono);
  }
}

.bm-ui-dr-sect {
  padding: 8px 0;
  border-bottom: 1px solid var(--ui-line);

  h4 {
    margin: 6px 20px;
    font-size: 12px;
    font-weight: 500;
    color: var(--ui-text-2);
  }

  &:last-child {
    border-bottom: 0;
  }
}

.bm-ui-dr-item {
  display: flex;
  align-items: center;
  gap: 20px;
  width: 100%;
  padding: 10px 20px;
  border: 0;
  background: none;
  cursor: pointer;
  font: 400 14px var(--ui-font);
  color: var(--ui-text);
  text-align: left;

  &:hover {
    background: var(--ui-hover);
  }

  .bm-ui-icon {
    color: var(--ui-text-2);
  }

  &.on {
    color: var(--ui-accent-text);
    font-weight: 500;

    .bm-ui-icon {
      color: var(--ui-accent-text);
    }
  }
}
</style>
