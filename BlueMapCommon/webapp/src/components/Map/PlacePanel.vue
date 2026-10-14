<template>
  <aside v-if="panel" v-show="!ui.state.panelCollapsed" class="bm-ui-panel" :class="{expanded: ui.state.sheetExpanded}" :aria-label="panel.title">
    <button class="bm-ui-sheet-handle" @click="ui.state.sheetExpanded = !ui.state.sheetExpanded" :aria-label="$t('ui.panel.resize')"><i></i></button>
    <div class="bm-ui-panel-body" ref="body">
      <PlaceView v-if="panel.view === 'place'" :place="panel" :key="panel.openedAt"/>
      <ListView v-else-if="panel.view === 'list'" :list="panel" :key="panel.openedAt"/>
      <SettingsView v-else-if="panel.view === 'settings'"/>
      <HelpView v-else-if="panel.view === 'help'"/>
    </div>
  </aside>
  <button v-if="panel" class="bm-ui-panel-toggle" :class="{collapsed: ui.state.panelCollapsed}" @click="ui.state.panelCollapsed = !ui.state.panelCollapsed"
          :aria-label="ui.state.panelCollapsed ? $t('ui.panel.expand') : $t('ui.panel.collapse')">
    <Icon :name="ui.state.panelCollapsed ? 'right' : 'left'" :size="20"/>
  </button>
</template>

<script>
import Icon from "./Icon.vue";
import PlaceView from "./PlaceView.vue";
import ListView from "./ListView.vue";
import SettingsView from "./SettingsView.vue";
import HelpView from "./HelpView.vue";

export default {
  name: "PlacePanel",
  components: {Icon, PlaceView, ListView, SettingsView, HelpView},
  data() {
    return {
      ui: this.$bluemap.ui,
    }
  },
  computed: {
    panel() {
      return this.ui.state.panel;
    }
  },
  watch: {
    panel(next, previous) {
      if (next && (!previous || next.openedAt !== previous.openedAt) && this.$refs.body) this.$refs.body.scrollTop = 0;
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-panel {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: $ui-panel-width;
  background: var(--ui-surface);
  color: var(--ui-text);
  box-shadow: var(--ui-shadow);
  z-index: 20;
  display: flex;
  flex-direction: column;
  pointer-events: auto;

  @media (max-width: $ui-mobile-break) {
    top: auto;
    right: 0;
    width: auto;
    height: 52%;
    border-radius: 16px 16px 0 0;
    padding-bottom: env(safe-area-inset-bottom, 0px);

    &.expanded {
      height: calc(100% - 64px - env(safe-area-inset-top, 0px));
    }
  }
}

.bm-ui-panel-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
}

.bm-ui-panel-spacer {
  height: 72px;

  @media (max-width: $ui-mobile-break) {
    display: none;
  }
}

.bm-ui-sheet-handle {
  display: none;
  place-items: center;
  height: 22px;
  border: 0;
  background: none;
  cursor: pointer;
  flex: none;
  width: 100%;

  i {
    width: 36px;
    height: 4px;
    border-radius: 2px;
    background: var(--ui-surface-3);
  }

  @media (max-width: $ui-mobile-break) {
    display: grid;
  }
}

.bm-ui-panel-toggle {
  position: absolute;
  top: 50%;
  left: $ui-panel-width;
  transform: translateY(-50%);
  width: 23px;
  height: 48px;
  border: 0;
  border-radius: 0 8px 8px 0;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  color: var(--ui-text-2);
  z-index: 19;
  display: grid;
  place-items: center;
  cursor: pointer;
  padding: 0;
  pointer-events: auto;

  &.collapsed {
    left: 0;
  }

  @media (max-width: $ui-mobile-break) {
    display: none;
  }
}
</style>
