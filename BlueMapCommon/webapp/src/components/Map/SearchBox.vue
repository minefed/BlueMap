<template>
  <div class="bm-ui-search" :class="{open: showSuggestions}" @focusout="onFocusOut">
    <div class="bm-ui-searchbox">
      <button class="bm-ui-icon-btn" @click="ui.state.drawerOpen = true" :aria-label="$t('menu.tooltip')" :title="$t('menu.tooltip')"><Icon name="menu"/></button>
      <input ref="input" v-model="ui.state.query" type="text" autocomplete="off" spellcheck="false"
             :placeholder="$t('ui.search.placeholder')" :aria-label="$t('ui.search.placeholder')"
             @focus="focused = true" @keydown="onKey" @input="cursor = -1">
      <button class="bm-ui-icon-btn" @click="submit" :aria-label="$t('ui.search.button')" :title="$t('ui.search.button')"><Icon name="search"/></button>
      <template v-if="ui.state.query || ui.state.panel">
        <span class="bm-ui-search-sep"></span>
        <button class="bm-ui-icon-btn" @click="clear" :aria-label="$t('ui.search.clear')" :title="$t('ui.search.clear')"><Icon name="close"/></button>
      </template>
    </div>
    <div v-if="showSuggestions" class="bm-ui-suggest" role="listbox">
      <template v-if="!query">
        <template v-if="ui.state.saved.length">
          <div class="bm-ui-suggest-head">{{ $t("ui.saved.title") }}</div>
          <button v-for="(entry, i) of ui.state.saved.slice(0, 4)" :key="entry.key" class="bm-ui-suggest-row" :class="{kb: cursor === i}"
                  @mousedown.prevent @click="openSaved(entry)">
            <span class="bm-ui-suggest-ico"><Icon name="bookmark" :size="20"/></span>
            <span class="bm-ui-suggest-main"><span class="bm-ui-suggest-title">{{ entry.title }}</span><span class="bm-ui-suggest-sub">{{ entry.kind }}</span></span>
          </button>
        </template>
      </template>
      <button v-for="(result, i) of results" :key="result.id" class="bm-ui-suggest-row" :class="{kb: cursor === i}" role="option"
              @mousedown.prevent @click="select(result)">
        <span class="bm-ui-suggest-ico" :style="result.color ? {color: result.color} : null">
          <img v-if="result.image" :src="result.image" alt="" :class="{pixelated: result.pixelated}" @error="$event.target.src = 'assets/steve.png'">
          <Icon v-else :name="result.icon || 'place'" :size="20"/>
        </span>
        <span class="bm-ui-suggest-main">
          <span class="bm-ui-suggest-title"><template v-for="(part, p) of highlight(result.title)" :key="p"><b v-if="part.match">{{ part.text }}</b><template v-else>{{ part.text }}</template></template></span>
          <span v-if="result.subtitle" class="bm-ui-suggest-sub">{{ result.subtitle }}</span>
        </span>
      </button>
      <div v-if="query && !results.length" class="bm-ui-suggest-hint">{{ $t("ui.search.noResults") }}</div>
      <div class="bm-ui-suggest-hint" v-html="$t('ui.search.coordinateHint')"></div>
    </div>
  </div>
</template>

<script>
import Icon from "./Icon.vue";
import {search} from "../../js/ui/search";

export default {
  name: "SearchBox",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      focused: false,
      cursor: -1,
    }
  },
  computed: {
    query() {
      return this.ui.state.query.trim();
    },
    results() {
      // depend on revision so add-on data refreshes are picked up
      this.ui.state.revision;
      return search(this.ui, this.query);
    },
    showSuggestions() {
      return this.focused;
    }
  },
  mounted() {
    window.addEventListener("keydown", this.globalKey);
  },
  beforeUnmount() {
    window.removeEventListener("keydown", this.globalKey);
  },
  methods: {
    highlight(title) {
      const text = String(title), index = this.query ? text.toLowerCase().indexOf(this.query.toLowerCase()) : -1;
      if (index < 0) return [{text, match: false}];
      const end = index + this.query.length;
      return [{text: text.slice(0, index), match: false}, {text: text.slice(index, end), match: true}, {text: text.slice(end), match: false}];
    },
    globalKey(evt) {
      if (evt.key !== "/" || evt.ctrlKey || evt.metaKey || evt.altKey) return;
      if (evt.target && evt.target.closest && evt.target.closest("input, textarea, select, [contenteditable]")) return;
      evt.preventDefault();
      this.$refs.input.focus();
    },
    onFocusOut(evt) {
      if (!this.$el.contains(evt.relatedTarget)) {
        this.focused = false;
        this.cursor = -1;
      }
    },
    onKey(evt) {
      const count = this.query ? this.results.length : Math.min(this.ui.state.saved.length, 4);
      if (evt.key === "ArrowDown") {
        this.cursor = Math.min(this.cursor + 1, count - 1);
        evt.preventDefault();
      } else if (evt.key === "ArrowUp") {
        this.cursor = Math.max(this.cursor - 1, -1);
        evt.preventDefault();
      } else if (evt.key === "Enter") {
        this.submit();
      } else if (evt.key === "Escape") {
        this.$refs.input.blur();
        this.focused = false;
      }
      // keep map keyboard controls from reacting while typing
      evt.stopPropagation();
    },
    submit() {
      if (!this.query) {
        if (this.cursor >= 0 && this.ui.state.saved[this.cursor]) this.openSaved(this.ui.state.saved[this.cursor]);
        else this.$refs.input.focus();
        return;
      }
      const result = this.results[Math.max(this.cursor, 0)];
      if (result) this.select(result);
    },
    select(result) {
      this.ui.state.query = result.title;
      this.close();
      result.select();
    },
    openSaved(entry) {
      this.close();
      this.ui.openSaved(entry);
    },
    close() {
      this.focused = false;
      this.cursor = -1;
      this.$refs.input.blur();
    },
    clear() {
      this.ui.state.query = "";
      this.ui.closePanel();
      this.ui.closeCard();
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-search {
  position: absolute;
  top: 12px;
  left: 16px;
  width: 392px;
  z-index: 30;
  pointer-events: auto;

  @media (max-width: $ui-mobile-break) {
    left: 12px;
    right: 12px;
    width: auto;
    top: calc(10px + env(safe-area-inset-top, 0px));
  }

  &.open .bm-ui-searchbox {
    border-radius: 24px 24px 0 0;
    box-shadow: var(--ui-shadow-sm);
  }
}

.bm-ui-searchbox {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 4px;
  background: var(--ui-surface);
  border-radius: 24px;
  box-shadow: var(--ui-shadow);

  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    background: transparent;
    color: var(--ui-text);
    font: 400 15px var(--ui-font);
    padding: 0 4px;

    &::placeholder {
      color: var(--ui-text-3);
    }
  }
}

// the rounded box already shows the focus, like the design
#app .bm-ui-searchbox input:focus-visible {
  outline: none;
}

.bm-ui-search-sep {
  width: 1px;
  height: 24px;
  background: var(--ui-line);
  margin: 0 2px;
}

.bm-ui-suggest {
  background: var(--ui-surface);
  border-radius: 0 0 16px 16px;
  box-shadow: var(--ui-shadow);
  border-top: 1px solid var(--ui-line);
  padding: 6px 0 8px;
  max-height: 60vh;
  overflow: auto;
}

.bm-ui-suggest-head {
  font-size: 12px;
  color: var(--ui-text-2);
  padding: 8px 16px 4px;
}

.bm-ui-suggest-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 8px 16px;
  border: 0;
  background: none;
  cursor: pointer;
  text-align: left;
  color: var(--ui-text);
  font: inherit;

  &:hover, &.kb {
    background: var(--ui-hover);
  }
}

.bm-ui-suggest-ico {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ui-surface-2);
  color: var(--ui-text-2);
  flex: none;

  img {
    width: 22px;
    height: 22px;
    border-radius: 4px;

    &.pixelated {
      image-rendering: pixelated;
    }
  }
}

.bm-ui-suggest-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.bm-ui-suggest-title {
  font-size: 14px;

  b {
    font-weight: 700;
  }
}

.bm-ui-suggest-sub {
  font-size: 12px;
  color: var(--ui-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bm-ui-suggest-hint {
  font-size: 12px;
  color: var(--ui-text-3);
  padding: 6px 16px 2px;

  code {
    font-family: var(--ui-font-mono);
    background: var(--ui-surface-2);
    padding: 1px 6px;
    border-radius: 4px;
    color: var(--ui-text-2);
  }
}
</style>
