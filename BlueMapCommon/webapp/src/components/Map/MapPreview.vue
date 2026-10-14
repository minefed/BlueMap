<template>
  <canvas ref="canvas" class="bm-ui-preview"></canvas>
</template>

<script>
export default {
  name: "MapPreview",
  props: {
    x: {type: Number, required: true},
    z: {type: Number, required: true},
    // blocks per css pixel
    scale: {type: Number, default: 0.34},
    map: {type: Object, default: null},
  },
  data() {
    return {
      viewer: this.$bluemap.mapViewer.data,
    }
  },
  computed: {
    signature() {
      const id = this.map ? this.map.data.id : (this.viewer.map ? this.viewer.map.id : "");
      return `${id}:${Math.round(this.x)}:${Math.round(this.z)}:${this.scale}`;
    }
  },
  watch: {
    signature() {
      this.draw();
    }
  },
  mounted() {
    this.draw();
  },
  methods: {
    async draw() {
      const map = this.map || this.$bluemap.mapViewer.map;
      if (!map) return;
      await this.$nextTick();
      const background = getComputedStyle(this.$el).getPropertyValue("--ui-surface-3").trim() || "#e8eaed";
      await this.$bluemap.ui.preview.render(this.$refs.canvas, map, this.x, this.z, this.scale, background);
    }
  }
}
</script>

<style lang="scss">
.bm-ui-preview {
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}
</style>
