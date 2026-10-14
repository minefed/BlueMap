<template>
  <div class="bm-ui-chips" v-if="chips.length">
    <button v-for="chip of chips" :key="chip.id" class="bm-ui-chip" :class="{on: ui.state.chip === chip.id}" @click="toggle(chip)">
      <Icon :name="chip.icon || 'place'" :size="18"/>{{ label(chip) }}
    </button>
  </div>
</template>

<script>
import Icon from "./Icon.vue";
import {hasListedMarkers, markerSetList, playersList, savedList} from "../../js/ui/lists";

export default {
  name: "ChipBar",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      markers: this.$bluemap.mapViewer.markers.data,
    }
  },
  computed: {
    chips() {
      this.ui.state.revision;
      const ui = this.ui, markerRoot = this.$bluemap.mapViewer.markers;
      // marker files load asynchronously; re-check once per UI tick
      this.ui.state.tick;
      this.markers.markerSets.length;
      const players = markerRoot.markerSets.get("bm-players");
      const builtIn = [
        {id: "players", label: () => this.$t("players.title"), icon: "person", order: 30, visible: () => !!players, open: () => ui.openList(playersList(ui))},
        {id: "markers", label: () => this.$t("markers.title"), icon: "flag", order: 40, visible: () => hasListedMarkers(markerRoot), open: () => ui.openList(markerSetList(ui))},
        {id: "saved", label: () => this.$t("ui.saved.chip"), icon: "bookmark", order: 90, open: () => ui.openList(savedList(ui))},
      ];
      return [...builtIn, ...this.ui.chips]
        .filter(chip => !chip.visible || chip.visible())
        .sort((a, b) => (a.order ?? 50) - (b.order ?? 50));
    }
  },
  methods: {
    label(chip) {
      return typeof chip.label === "function" ? chip.label() : chip.label;
    },
    toggle(chip) {
      if (this.ui.state.chip === chip.id) {
        this.ui.closePanel();
        return;
      }
      this.ui.state.query = "";
      chip.open();
      this.ui.state.chip = chip.id;
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-chips {
  position: absolute;
  top: 20px;
  left: 424px;
  right: 200px;
  z-index: 25;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  padding: 0 4px 6px;
  pointer-events: none;

  &::-webkit-scrollbar {
    display: none;
  }

  .bm-ui-panel-open & {
    left: 436px;
  }

  @media (max-width: $ui-mobile-break) {
    &, .bm-ui-panel-open & {
      top: calc(68px + env(safe-area-inset-top, 0px));
      left: 0;
      right: 0;
      padding: 0 12px 6px;
    }
  }
}

.bm-ui-chip {
  pointer-events: auto;
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px 0 10px;
  border-radius: 16px;
  border: 0;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  font: 500 13px var(--ui-font);
  color: var(--ui-text);
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: var(--ui-surface-2);
  }

  &.on {
    background: var(--ui-accent-weak);
    color: var(--ui-accent-text);
  }
}
</style>
