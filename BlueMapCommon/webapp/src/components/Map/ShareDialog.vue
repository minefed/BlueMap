<template>
  <template v-if="share">
    <div class="bm-ui-scrim bm-ui-share-scrim" @click="ui.closeShare()"></div>
    <div class="bm-ui-dialog" role="dialog" aria-modal="true" :aria-label="$t('ui.share.title')" @keydown.esc="ui.closeShare()">
      <div class="bm-ui-dialog-head">
        <h2>{{ $t("ui.share.title") }}</h2>
        <button class="bm-ui-icon-btn" @click="ui.closeShare()" :aria-label="$t('ui.close')"><Icon name="close"/></button>
      </div>
      <div class="bm-ui-dialog-place">
        <span class="bm-ui-dialog-thumb"><MapPreview :x="share.x" :z="share.z" :scale="0.5"/></span>
        <div>
          <b>{{ share.title }}</b>
          <span>{{ ui.formatCoordinates(share) }} · {{ mapName }}</span>
        </div>
      </div>
      <div class="bm-ui-dialog-link">
        <input ref="link" id="bm-share-link" type="text" readonly :value="link" :aria-label="$t('ui.share.link')" @focus="$event.target.select()">
        <button class="bm-ui-mini-btn on" @click="copy">{{ $t("ui.share.copy") }}</button>
      </div>
      <div class="bm-ui-set-row">
        <span>{{ $t("ui.share.includeView") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(includeView)" :aria-label="$t('ui.share.includeView')" @click="includeView = !includeView"><span></span></button>
      </div>
    </div>
  </template>
</template>

<script>
import Icon from "./Icon.vue";
import MapPreview from "./MapPreview.vue";

export default {
  name: "ShareDialog",
  components: {Icon, MapPreview},
  data() {
    return {
      ui: this.$bluemap.ui,
      viewer: this.$bluemap.mapViewer.data,
      includeView: true,
    }
  },
  computed: {
    share() {
      return this.ui.state.share;
    },
    mapName() {
      return this.viewer.map ? this.viewer.map.name : "";
    },
    link() {
      return this.share ? this.ui.shareLink(this.share, this.includeView) : "";
    }
  },
  watch: {
    share(share) {
      if (share) this.$nextTick(() => this.$refs.link && this.$refs.link.focus());
    }
  },
  methods: {
    copy() {
      this.$refs.link.select();
      this.ui.copy(this.link, this.$t("ui.share.copied"));
    }
  }
}
</script>

<style lang="scss">
.bm-ui-scrim {
  position: absolute;
  inset: 0;
  z-index: 50;
  background: var(--ui-scrim);
  pointer-events: auto;

  &.bm-ui-share-scrim {
    z-index: 65;
  }
}

.bm-ui-dialog {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 66;
  width: min(440px, calc(100% - 32px));
  box-sizing: border-box;
  background: var(--ui-surface);
  color: var(--ui-text);
  border-radius: 12px;
  box-shadow: var(--ui-shadow);
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  pointer-events: auto;
}

.bm-ui-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-right: -8px;

  h2 {
    margin: 0;
    padding: 0;
    width: auto;
    font-size: 18px;
    font-weight: 500;

    &:after {
      display: none;
    }
  }
}

.bm-ui-dialog-place {
  display: flex;
  gap: 14px;
  align-items: center;

  b {
    display: block;
    font-size: 15px;
    font-weight: 500;
    overflow-wrap: anywhere;
  }

  span {
    font-size: 13px;
    color: var(--ui-text-2);
    font-family: var(--ui-font-mono);
  }
}

.bm-ui-dialog-thumb {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  background: var(--ui-surface-3);
}

.bm-ui-dialog-link {
  display: flex;
  gap: 8px;
  align-items: center;

  input {
    flex: 1;
    min-width: 0;
    height: 36px;
    box-sizing: border-box;
    border: 1px solid var(--ui-line);
    border-radius: 8px;
    padding: 0 10px;
    font: 400 12px var(--ui-font-mono);
    color: var(--ui-text);
    background: var(--ui-surface-2);
  }
}
</style>
