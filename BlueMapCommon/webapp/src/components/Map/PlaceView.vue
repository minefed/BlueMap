<template>
  <div class="bm-ui-place">
    <div v-if="showHero" class="bm-ui-hero">
      <MapPreview :x="position.x" :z="position.z" :scale="place.previewScale || 0.34"/>
      <span class="bm-ui-hero-pin" :style="heroPinStyle"><Icon :name="place.icon || 'place'" :size="18"/></span>
      <span class="bm-ui-hero-tag">{{ $t("ui.place.preview") }}</span>
    </div>
    <div v-else class="bm-ui-panel-spacer"></div>

    <button v-if="canGoBack" class="bm-ui-back" @click="ui.panelBack()"><Icon name="back" :size="18"/>{{ $t("ui.panel.back") }}</button>

    <div class="bm-ui-place-head" :class="{'with-image': !!place.image}">
      <img v-if="place.image" class="bm-ui-place-image" :class="{pixelated: place.pixelated}" :src="place.image" alt="" @error="$event.target.src = 'assets/steve.png'">
      <div class="bm-ui-place-titles">
        <h1>{{ place.title }}</h1>
        <div class="bm-ui-place-sub" v-if="subtitleLine">{{ subtitleLine }}</div>
        <div class="bm-ui-badges" v-if="badges.length">
          <span v-for="(badge, i) of badges" :key="i" class="bm-ui-badge" :style="{background: badge.color || 'var(--ui-text-2)', color: badge.textColor || '#fff'}">{{ badge.label }}</span>
        </div>
      </div>
    </div>

    <div class="bm-ui-actions" v-if="actions.length">
      <button v-for="(action, i) of actions" :key="i" class="bm-ui-act" :class="{primary: action.primary, on: action.active}" @click="action.onClick()">
        <span class="bm-ui-act-circ"><Icon :name="action.icon || 'place'" :size="20"/></span>{{ action.label }}
      </button>
    </div>

    <div class="bm-ui-tabs" v-if="tabs.length">
      <button v-for="tab of tabs" :key="tab.id" class="bm-ui-tab" :class="{on: tab.id === activeTab}" @click="selectTab(tab.id)">{{ tab.label }}</button>
    </div>

    <div v-if="place.description" class="bm-ui-section"><p>{{ place.description }}</p></div>
    <div v-if="place.html" class="bm-ui-section bm-ui-marker-detail" v-html="place.html"></div>

    <div ref="mount" class="bm-ui-place-mount"></div>

    <div v-for="(row, i) of rows" :key="i" class="bm-ui-info-row">
      <Icon :name="row.icon || 'info'"/>
      <div>
        <button v-if="row.onClick" class="bm-ui-linkish" :class="{mono: row.mono}" @click="row.onClick()">{{ row.text }}</button>
        <span v-else :class="{mono: row.mono}">{{ row.text }}</span>
        <div class="bm-ui-muted" v-if="row.sub">{{ row.sub }}</div>
      </div>
    </div>
  </div>
</template>

<script>
import Icon from "./Icon.vue";
import MapPreview from "./MapPreview.vue";

const value = (v, ...args) => typeof v === "function" ? v(...args) : v;

export default {
  name: "PlaceView",
  components: {Icon, MapPreview},
  props: {
    place: {type: Object, required: true},
  },
  data() {
    return {
      ui: this.$bluemap.ui,
      activeTab: this.place.tabs && this.place.tabs.length ? this.place.tabs[0].id : null,
      cleanup: null,
    }
  },
  computed: {
    live() {
      // re-evaluate function fields every second for live places
      return this.place.live ? this.ui.state.tick : 0;
    },
    position() {
      this.live;
      return value(this.place.position);
    },
    showHero() {
      return this.position && this.place.preview !== false && !!this.$bluemap.mapViewer.data.map;
    },
    heroPinStyle() {
      return this.place.color ? {background: this.place.color} : null;
    },
    canGoBack() {
      return this.ui.state.panelStack.length > 0;
    },
    subtitleLine() {
      this.live;
      return [this.place.kind, value(this.place.subtitle)].filter(Boolean).join(" · ");
    },
    badges() {
      this.live;
      return value(this.place.badges) || [];
    },
    rows() {
      this.live;
      this.ui.state.revision;
      return value(this.place.rows) || [];
    },
    tabs() {
      return value(this.place.tabs) || [];
    },
    actions() {
      this.live;
      this.ui.state.saved.length;
      const custom = value(this.place.actions) || [];
      const result = [...custom];
      const position = this.position;
      if (this.place.defaultActions !== false && position) {
        if (!custom.some(action => action.primary)) {
          result.unshift({icon: "near", label: this.$t("ui.place.moveTo"), primary: true, onClick: () => this.ui.flyTo(position, {distance: Math.min(this.$bluemap.mapViewer.controlsManager.distance, 200)})});
        }
        result.push({icon: "copy", label: this.$t("ui.place.copyCoordinates"), onClick: () => this.ui.copyCoordinates(position)});
        result.push({icon: "share", label: this.$t("ui.share.button"), onClick: () => this.ui.openShare({title: this.place.title, ...position})});
        if (this.place.key) {
          const saved = this.ui.isSaved(this.place.key);
          result.push({
            icon: saved ? "bookmark" : "bookmarkOutline",
            label: saved ? this.$t("ui.saved.saved") : this.$t("ui.saved.save"),
            active: saved,
            onClick: () => this.ui.toggleSaved({key: this.place.key, title: this.place.title, kind: this.place.kind, position}),
          });
        }
      }
      return result;
    }
  },
  mounted() {
    this.mountCustom();
  },
  beforeUnmount() {
    this.unmountCustom();
  },
  methods: {
    mountCustom() {
      this.unmountCustom();
      const el = this.$refs.mount;
      const tab = this.tabs.find(t => t.id === this.activeTab);
      const mount = tab && tab.mount ? tab.mount : this.place.mount;
      if (!el || !mount) return;
      try {
        const cleanup = mount(el);
        this.cleanup = typeof cleanup === "function" ? cleanup : null;
      } catch (e) {
        console.error("Place content failed to mount", e);
      }
    },
    unmountCustom() {
      if (this.cleanup) {
        try { this.cleanup(); } catch (e) { console.error(e); }
      }
      this.cleanup = null;
      if (this.$refs.mount) this.$refs.mount.replaceChildren();
    },
    selectTab(id) {
      this.activeTab = id;
      this.$nextTick(() => this.mountCustom());
    }
  }
}
</script>

<style lang="scss">
.bm-ui-hero {
  position: relative;
  height: 230px;
  background: var(--ui-surface-3);
  overflow: hidden;

  @media (max-width: 640px) {
    height: 150px;
  }

  &:after {
    content: "";
    position: absolute;
    inset: 55% 0 0 0;
    background: linear-gradient(transparent, rgba(0, 0, 0, .35));
    pointer-events: none;
  }
}

.bm-ui-hero-pin {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #ea4335;
  color: #fff;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .4);
  z-index: 1;
}

.bm-ui-hero-tag {
  position: absolute;
  left: 16px;
  bottom: 12px;
  z-index: 1;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, .55);
  padding: 3px 8px;
  border-radius: 4px;
}

.bm-ui-back {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px 0;
  border: 0;
  background: none;
  color: var(--ui-accent-text);
  font: 500 13px var(--ui-font);
  cursor: pointer;
}

.bm-ui-place-head {
  padding: 20px 24px 12px;

  &.with-image {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  h1 {
    margin: 0;
    padding: 0;
    width: auto;
    font-size: 22px;
    font-weight: 500;
    line-height: 1.3;
    text-align: left;
    text-wrap: balance;
    overflow-wrap: anywhere;

    &:after {
      display: none;
    }
  }
}

.bm-ui-place-titles {
  min-width: 0;
}

.bm-ui-place-image {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  flex: none;

  &.pixelated {
    image-rendering: pixelated;
  }
}

.bm-ui-place-sub {
  margin-top: 4px;
  font-size: 14px;
  color: var(--ui-text-2);
}

.bm-ui-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.bm-ui-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;

  &.sm {
    height: 18px;
    padding: 0 5px;
    font-size: 11px;
  }
}

.bm-ui-actions {
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 4px;
  padding: 8px 12px 16px;
  border-bottom: 1px solid var(--ui-line);
}

.bm-ui-act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 72px;
  border: 0;
  background: none;
  cursor: pointer;
  font: 400 12px var(--ui-font);
  color: var(--ui-accent-text);
  text-align: center;

  .bm-ui-act-circ {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    border: 1px solid var(--ui-line);
    color: var(--ui-accent-text);
  }

  &:hover .bm-ui-act-circ {
    background: var(--ui-accent-weak);
  }

  &.primary .bm-ui-act-circ, &.on .bm-ui-act-circ {
    background: var(--ui-accent);
    border-color: var(--ui-accent);
    color: var(--ui-on-accent);
  }
}

.bm-ui-tabs {
  display: flex;
  border-bottom: 1px solid var(--ui-line);
  padding: 0 8px;
  position: sticky;
  top: 0;
  background: var(--ui-surface);
  z-index: 2;
}

.bm-ui-tab {
  flex: 1;
  height: 48px;
  border: 0;
  background: none;
  cursor: pointer;
  font: 500 14px var(--ui-font);
  color: var(--ui-text-2);
  border-bottom: 3px solid transparent;

  &.on {
    color: var(--ui-accent-text);
    border-bottom-color: var(--ui-accent-text);
  }
}

.bm-ui-section {
  padding: 16px 24px;

  p {
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: var(--ui-text-2);
  }
}

.bm-ui-marker-detail {
  font-size: 14px;
  line-height: 1.6;
  color: var(--ui-text);
  overflow-wrap: anywhere;

  img {
    max-width: 100%;
  }
}

.bm-ui-place-mount:empty {
  display: none;
}

.bm-ui-info-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  padding: 12px 24px;
  font-size: 14px;
  line-height: 1.5;

  > .bm-ui-icon {
    color: var(--ui-accent-text);
    margin-top: 1px;
  }

  .mono {
    font-family: var(--ui-font-mono);
    font-size: 13px;
  }
}

.bm-ui-linkish {
  border: 0;
  background: none;
  padding: 0;
  cursor: pointer;
  text-align: left;
  color: var(--ui-text);
  font: inherit;

  &:hover {
    text-decoration: underline;
  }
}

.bm-ui-muted {
  color: var(--ui-text-2);
  font-size: 13px;
}
</style>
