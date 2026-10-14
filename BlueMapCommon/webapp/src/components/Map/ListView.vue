<template>
  <div class="bm-ui-list">
    <div class="bm-ui-panel-spacer"></div>
    <button v-if="canGoBack" class="bm-ui-back" @click="ui.panelBack()"><Icon name="back" :size="18"/>{{ $t("ui.panel.back") }}</button>
    <div class="bm-ui-list-title">
      <h1>{{ list.title }}</h1>
      <span>{{ subtitle }}</span>
    </div>
    <div v-if="!items.length" class="bm-ui-empty">
      <Icon :name="list.emptyIcon || 'info'"/>
      <p>{{ list.empty || $t("ui.list.empty") }}</p>
    </div>
    <button v-for="item of items" :key="item.key || item.title" class="bm-ui-row" :class="{muted: item.muted, selected: item.key && item.key === ui.state.selectedKey}" @click="item.onClick && item.onClick()">
      <span class="bm-ui-row-ico" :style="item.color ? {background: item.color} : null" :class="{image: !!item.image}">
        <img v-if="item.image" :src="item.image" alt="" :class="{pixelated: item.pixelated}" @error="$event.target.src = 'assets/steve.png'">
        <Icon v-else :name="item.icon || 'place'" :size="20"/>
      </span>
      <span class="bm-ui-row-main">
        <span class="bm-ui-row-title">{{ item.title }}</span>
        <span class="bm-ui-row-sub" v-if="item.subtitle || (item.badges && item.badges.length)">
          <span v-for="(badge, i) of item.badges || []" :key="i" class="bm-ui-badge sm" :style="{background: badge.color || 'var(--ui-text-2)', color: badge.textColor || '#fff'}">{{ badge.label }}</span>
          <span v-if="item.subtitle">{{ item.subtitle }}</span>
        </span>
      </span>
      <span v-if="item.action" class="bm-ui-mini-btn" :class="{on: item.action.active}" role="button" tabindex="0"
            :title="item.action.label" @click.stop="item.action.onClick()" @keydown.enter.stop.prevent="item.action.onClick()">
        <Icon :name="item.action.icon || 'target'" :size="18"/><span class="bm-ui-mini-label">{{ item.action.label }}</span>
      </span>
      <span v-else-if="item.meta" class="bm-ui-row-meta">{{ item.meta }}</span>
    </button>
    <p v-if="list.footnote && items.length" class="bm-ui-footnote">{{ list.footnote }}</p>
  </div>
</template>

<script>
import Icon from "./Icon.vue";

export default {
  name: "ListView",
  components: {Icon},
  props: {
    list: {type: Object, required: true},
  },
  data() {
    return {
      ui: this.$bluemap.ui,
    }
  },
  computed: {
    items() {
      // lists are re-read every UI tick so players, distances and add-on data stay current
      this.ui.state.tick;
      this.ui.state.revision;
      this.ui.state.saved.length;
      const items = typeof this.list.items === "function" ? this.list.items() : this.list.items;
      return Array.isArray(items) ? items : [];
    },
    subtitle() {
      if (this.list.subtitle) return typeof this.list.subtitle === "function" ? this.list.subtitle() : this.list.subtitle;
      return this.$t("ui.list.count", {count: this.items.length});
    },
    canGoBack() {
      return this.ui.state.panelStack.length > 0;
    }
  }
}
</script>

<style lang="scss">
.bm-ui-list-title {
  padding: 4px 24px 8px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;

  h1 {
    margin: 0;
    padding: 0;
    width: auto;
    font-size: 20px;
    font-weight: 500;
    text-align: left;

    &:after {
      display: none;
    }
  }

  span {
    font-size: 13px;
    color: var(--ui-text-2);
    white-space: nowrap;
  }
}

.bm-ui-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 12px 24px;
  border: 0;
  border-bottom: 1px solid var(--ui-line);
  background: none;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: var(--ui-text);

  &:hover, &.selected {
    background: var(--ui-hover);
  }

  &.muted .bm-ui-row-main, &.muted .bm-ui-row-ico {
    opacity: .55;
  }
}

.bm-ui-row-ico {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex: none;
  color: #fff;
  background: var(--ui-accent);

  &.image {
    background: var(--ui-surface-2);
    border-radius: 8px;
  }

  img {
    width: 40px;
    height: 40px;
    border-radius: 8px;

    &.pixelated {
      image-rendering: pixelated;
    }
  }
}

.bm-ui-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.bm-ui-row-title {
  font-size: 15px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.bm-ui-row-sub {
  font-size: 13px;
  color: var(--ui-text-2);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.bm-ui-row-meta {
  font-size: 12px;
  color: var(--ui-text-2);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.bm-ui-mini-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border-radius: 16px;
  border: 1px solid var(--ui-line);
  background: var(--ui-surface);
  color: var(--ui-accent-text);
  font: 500 13px var(--ui-font);
  cursor: pointer;
  flex: none;
  white-space: nowrap;

  &.on {
    background: var(--ui-accent);
    color: var(--ui-on-accent);
    border-color: var(--ui-accent);
  }

  &:hover:not(.on) {
    background: var(--ui-accent-weak);
  }

  @media (max-width: 400px) {
    .bm-ui-mini-label {
      display: none;
    }
  }
}

.bm-ui-empty {
  padding: 32px 24px;
  text-align: center;
  color: var(--ui-text-2);
  font-size: 14px;
  line-height: 1.6;

  .bm-ui-icon {
    width: 40px;
    height: 40px;
    color: var(--ui-text-3);
  }

  p {
    margin: 8px 0 0;
  }
}

.bm-ui-footnote {
  margin: 0;
  padding: 16px 24px;
  font-size: 12px;
  color: var(--ui-text-2);
}
</style>
