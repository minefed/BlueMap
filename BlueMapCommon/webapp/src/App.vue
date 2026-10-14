<template>
  <div id="app" :class="rootClasses">
    <FreeFlightMobileControls v-if="mapViewer.mapState === 'loaded' && appState.controls.state === 'free'" />
    <div v-if="mapViewer.mapState !== 'loaded'" class="map-state-message">{{ $t("map." + mapViewer.mapState) }}</div>

    <PlacePanel />
    <SearchBox />
    <ChipBar />
    <OnlinePlayers />
    <LayersControl v-if="showMapUi" />
    <MapButtons v-if="showMapUi" />
    <StatusBar />
    <LocationCard />
    <ContextMenu />
    <NavDrawer />
    <ShareDialog />
    <div v-if="ui.state.toast" class="bm-ui-toast" role="status">{{ ui.state.toast }}</div>
  </div>
</template>

<script>
import FreeFlightMobileControls from "./components/Controls/FreeFlightMobileControls.vue";
import PlacePanel from "./components/Map/PlacePanel.vue";
import SearchBox from "./components/Map/SearchBox.vue";
import ChipBar from "./components/Map/ChipBar.vue";
import OnlinePlayers from "./components/Map/OnlinePlayers.vue";
import LayersControl from "./components/Map/LayersControl.vue";
import MapButtons from "./components/Map/MapButtons.vue";
import StatusBar from "./components/Map/StatusBar.vue";
import LocationCard from "./components/Map/LocationCard.vue";
import ContextMenu from "./components/Map/ContextMenu.vue";
import NavDrawer from "./components/Map/NavDrawer.vue";
import ShareDialog from "./components/Map/ShareDialog.vue";

export default {
  name: 'App',
  components: {
    FreeFlightMobileControls,
    PlacePanel,
    SearchBox,
    ChipBar,
    OnlinePlayers,
    LayersControl,
    MapButtons,
    StatusBar,
    LocationCard,
    ContextMenu,
    NavDrawer,
    ShareDialog,
  },
  computed: {
    showMapUi() {
      return this.mapViewer.mapState === "loading" || this.mapViewer.mapState === "loaded";
    },
    rootClasses() {
      return {
        'theme-light': this.appState.theme === 'light',
        'theme-dark': this.appState.theme === 'dark',
        'theme-contrast': this.appState.theme === 'contrast',
        'bm-ui-panel-open': !!this.ui.state.panel && !this.ui.state.panelCollapsed,
        'bm-ui-sheet-full': !!this.ui.state.panel && this.ui.state.sheetExpanded,
      };
    }
  },
  data() {
    return {
      appState: this.$bluemap.appState,
      mapViewer: this.$bluemap.mapViewer.data,
      ui: this.$bluemap.ui,
    }
  },
  mounted() {
    window.addEventListener("keydown", this.onKey);
  },
  beforeUnmount() {
    window.removeEventListener("keydown", this.onKey);
  },
  methods: {
    onKey(evt) {
      if (evt.key !== "Escape") return;
      const state = this.ui.state;
      if (state.share) this.ui.closeShare();
      else if (state.contextMenu) this.ui.closeContextMenu();
      else if (state.drawerOpen) state.drawerOpen = false;
      else if (state.layersOpen) state.layersOpen = false;
      else if (state.card) this.ui.closeCard();
      else if (state.panel) this.ui.closePanel();
    }
  }
}
</script>

<style lang="scss">
  @import "./scss/global.scss";

  #map-container {
    position: absolute;
    width: 100%;
    height: 100%;
  }

  #app {
    position: absolute;
    width: 100%;
    height: 100%;
    overflow: hidden;

    z-index: 10000; // put over bluemap markers

    pointer-events: none;

    font: 400 14px/1.4 var(--ui-font);
    color: var(--ui-text);

    .map-state-message {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      color: var(--ui-text);
      background: var(--ui-surface);
      box-shadow: var(--ui-shadow-sm);
      padding: 10px 18px;
      border-radius: 20px;
      line-height: 1.4;
      text-align: center;
    }
  }

  .bm-ui-toast {
    position: absolute;
    left: 50%;
    bottom: calc(24px + env(safe-area-inset-bottom, 0px));
    transform: translateX(-50%);
    z-index: 70;
    background: var(--ui-toast-bg);
    color: var(--ui-toast-fg);
    padding: 12px 20px;
    border-radius: 6px;
    font-size: 14px;
    box-shadow: 0 3px 8px rgba(0, 0, 0, .35);
    max-width: calc(100% - 32px);
    box-sizing: border-box;
    overflow-wrap: anywhere;
  }
</style>
