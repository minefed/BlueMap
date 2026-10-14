<template>
  <div class="bm-ui-statusbar">
    <span class="bm-ui-status-coords" v-if="cursor">X {{ cursor.x }} · Z {{ cursor.z }}</span>
    <span class="bm-ui-scalebar" v-if="scale"><i :style="{width: scale.px + 'px'}"></i>{{ scale.label }}</span>
    <span class="bm-ui-status-brand">BlueMap</span>
  </div>
</template>

<script>
const NICE = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000];

export default {
  name: "StatusBar",
  data() {
    return {
      cursor: null,
      scale: null,
      frame: 0,
      pending: null,
    }
  },
  mounted() {
    this.canvas = this.$bluemap.mapViewer.renderer.domElement;
    this.canvas.addEventListener("pointermove", this.onMove);
    this.canvas.addEventListener("pointerleave", this.onLeave);
    this.$bluemap.events.addEventListener("bluemapCameraMoved", this.updateScale);
    window.addEventListener("resize", this.updateScale);
    this.updateScale();
  },
  beforeUnmount() {
    this.canvas.removeEventListener("pointermove", this.onMove);
    this.canvas.removeEventListener("pointerleave", this.onLeave);
    this.$bluemap.events.removeEventListener("bluemapCameraMoved", this.updateScale);
    window.removeEventListener("resize", this.updateScale);
    cancelAnimationFrame(this.frame);
  },
  methods: {
    onMove(evt) {
      if (evt.pointerType !== "mouse") return;
      this.pending = {x: evt.clientX, y: evt.clientY};
      if (!this.frame) this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        const point = this.pending && this.$bluemap.ui.pickPlane(this.pending.x, this.pending.y);
        this.cursor = point ? {x: Math.floor(point.x), z: Math.floor(point.z)} : null;
      });
    },
    onLeave() {
      this.cursor = null;
    },
    updateScale() {
      const wpp = this.$bluemap.ui.worldPerPixel();
      if (!isFinite(wpp) || wpp <= 0) return;
      let blocks = NICE[0];
      for (const value of NICE) if (value / wpp <= 90) blocks = value;
      this.scale = {px: Math.max(8, Math.round(blocks / wpp)), label: this.$t("ui.blocks", {count: blocks.toLocaleString(this.$i18n.locale)})};
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-statusbar {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 14;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 22px;
  padding: 0 10px;
  background: color-mix(in srgb, var(--ui-surface) 82%, transparent);
  font: 400 11px var(--ui-font);
  color: var(--ui-text-2);
  border-top-left-radius: 6px;
  font-variant-numeric: tabular-nums;
  pointer-events: auto;

  @media (max-width: $ui-mobile-break) {
    .bm-ui-status-coords, .bm-ui-status-brand {
      display: none;
    }
  }
}

.bm-ui-status-coords {
  font-family: var(--ui-font-mono);
  min-width: 130px;
}

.bm-ui-scalebar {
  display: flex;
  align-items: center;
  gap: 6px;

  i {
    display: block;
    height: 6px;
    border: 1.5px solid var(--ui-text-2);
    border-top: 0;
  }
}
</style>
