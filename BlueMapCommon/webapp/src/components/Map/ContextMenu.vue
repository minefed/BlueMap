<template>
  <div v-if="menu" ref="menu" class="bm-ui-ctx" :style="style" role="menu" @keydown.esc="ui.closeContextMenu()">
    <button class="bm-ui-ctx-coord" role="menuitem" @click="act(point => ui.copyCoordinates(point))">{{ coordinates }}</button>
    <hr>
    <button role="menuitem" @click="act(point => ui.showLocation({...point, hires: point.y !== null, pin: true}))"><Icon name="place" :size="20"/>{{ $t("ui.context.whatsHere") }}</button>
    <button role="menuitem" @click="act(point => ui.flyTo(point))"><Icon name="near" :size="20"/>{{ $t("ui.context.center") }}</button>
    <button role="menuitem" @click="act(point => ui.startMeasure(point))"><Icon name="ruler" :size="20"/>{{ $t("ui.measure.button") }}</button>
    <button role="menuitem" @click="act((point, title) => ui.openShare({title, ...point}))"><Icon name="share" :size="20"/>{{ $t("ui.context.share") }}</button>
    <button role="menuitem" @click="act(save)"><Icon name="bookmarkOutline" :size="20"/>{{ $t("ui.context.save") }}</button>
  </div>
</template>

<script>
import Icon from "./Icon.vue";

export default {
  name: "ContextMenu",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      viewer: this.$bluemap.mapViewer.data,
      size: {width: 230, height: 280},
    }
  },
  computed: {
    menu() {
      return this.ui.state.contextMenu;
    },
    style() {
      const root = this.$bluemap.mapViewer.rootElement;
      const left = Math.max(8, Math.min(this.menu.left, root.clientWidth - this.size.width - 8));
      const top = Math.max(8, Math.min(this.menu.top, root.clientHeight - this.size.height - 8));
      return {left: left + "px", top: top + "px"};
    },
    title() {
      const p = this.menu.point, address = this.ui.addressAt(p.x, p.z);
      return `${address ? address.label : (this.viewer.map ? this.viewer.map.name : "")} (${p.x}, ${p.z})`;
    },
    coordinates() {
      const p = this.menu.point;
      return (p.y === null ? [p.x, p.z] : [p.x, p.y, p.z]).join(", ");
    }
  },
  watch: {
    menu(menu) {
      if (menu) this.$nextTick(() => {
        if (!this.$refs.menu) return;
        this.size = {width: this.$refs.menu.offsetWidth, height: this.$refs.menu.offsetHeight};
        const first = this.$refs.menu.querySelector("button");
        if (first) first.focus({preventScroll: true});
      });
    }
  },
  mounted() {
    this.canvas = this.$bluemap.mapViewer.renderer.domElement;
    this.canvas.addEventListener("contextmenu", this.onContextMenu);
    this.canvas.addEventListener("pointerdown", this.onDown);
    window.addEventListener("pointerup", this.onUp);
    window.addEventListener("pointerdown", this.onOutside, true);
  },
  beforeUnmount() {
    this.canvas.removeEventListener("contextmenu", this.onContextMenu);
    this.canvas.removeEventListener("pointerdown", this.onDown);
    window.removeEventListener("pointerup", this.onUp);
    window.removeEventListener("pointerdown", this.onOutside, true);
  },
  methods: {
    onContextMenu(evt) {
      evt.preventDefault();
    },
    onDown(evt) {
      // right-drag rotates the camera; only an unmoved right click opens the menu
      this.down = evt.button === 2 ? {x: evt.clientX, y: evt.clientY} : null;
    },
    onUp(evt) {
      if (evt.button !== 2 || !this.down) return;
      const moved = Math.hypot(evt.clientX - this.down.x, evt.clientY - this.down.y) > 4;
      this.down = null;
      if (!moved) this.ui.openContextMenu(evt.clientX, evt.clientY);
    },
    onOutside(evt) {
      if (this.menu && this.$refs.menu && !this.$refs.menu.contains(evt.target)) this.ui.closeContextMenu();
    },
    act(action) {
      // read the clicked point before closing, the menu state is gone afterwards
      const point = {...this.menu.point}, title = this.title;
      this.ui.closeContextMenu();
      action(point, title);
    },
    save(point, title) {
      this.ui.toggleSaved({key: this.ui.coordinateKey(point), title, kind: this.$t("ui.location.coordinates"), position: point});
    }
  }
}
</script>

<style lang="scss">
.bm-ui-ctx {
  position: absolute;
  z-index: 60;
  min-width: 220px;
  background: var(--ui-surface);
  color: var(--ui-text);
  border-radius: 8px;
  box-shadow: var(--ui-shadow);
  padding: 6px 0;
  pointer-events: auto;

  button {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 8px 16px;
    border: 0;
    background: none;
    cursor: pointer;
    font: 400 14px var(--ui-font);
    color: var(--ui-text);
    text-align: left;

    &:hover, &:focus-visible {
      background: var(--ui-hover);
      outline: none;
    }

    .bm-ui-icon {
      color: var(--ui-text-2);
    }
  }

  .bm-ui-ctx-coord {
    font: 500 13px var(--ui-font-mono);
  }

  hr {
    border: 0;
    border-top: 1px solid var(--ui-line);
    margin: 6px 0;
  }
}
</style>
