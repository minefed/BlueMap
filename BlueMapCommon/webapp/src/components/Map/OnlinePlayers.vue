<template>
  <div class="bm-ui-topright" v-if="hasPlayerSet" ref="root">
    <button class="bm-ui-online-btn" @click="open = !open" :aria-expanded="String(open)" :aria-label="$t('ui.player.onlineCount', {count: players.length})">
      <span class="bm-ui-online-dot"></span>
      <span class="bm-ui-online-label">{{ $t("ui.player.onlineCount", {count: players.length}) }}</span>
      <Icon v-if="!players.length" name="group" :size="20" class="bm-ui-online-icon"/>
      <span class="bm-ui-avatars" v-if="players.length">
        <img v-for="player of players.slice(0, 3)" :key="player.playerUuid" :src="ui.playerHead(player)" :alt="player.name" @error="$event.target.src = 'assets/steve.png'">
      </span>
    </button>
    <div v-if="open" class="bm-ui-online-pop">
      <h3>{{ $t("ui.player.onlineCount", {count: players.length}) }}</h3>
      <p v-if="!players.length" class="bm-ui-online-empty">{{ $t("ui.player.none") }}</p>
      <button v-for="player of players" :key="player.playerUuid" class="bm-ui-row compact" @click="openPlayer(player)">
        <span class="bm-ui-row-ico image"><img :src="ui.playerHead(player)" alt="" class="pixelated" @error="$event.target.src = 'assets/steve.png'"></span>
        <span class="bm-ui-row-main">
          <span class="bm-ui-row-title">{{ player.name }}</span>
          <span class="bm-ui-row-sub">{{ where(player) }}</span>
        </span>
        <span class="bm-ui-mini-btn" :class="{on: ui.isFollowing(player.playerUuid)}" role="button" tabindex="0" @click.stop="ui.toggleFollow(player)" @keydown.enter.stop.prevent="ui.toggleFollow(player)">
          {{ ui.isFollowing(player.playerUuid) ? $t("ui.player.following") : $t("markers.followPlayerTitle") }}
        </span>
      </button>
    </div>
  </div>
</template>

<script>
import Icon from "./Icon.vue";

export default {
  name: "OnlinePlayers",
  components: {Icon},
  data() {
    return {
      ui: this.$bluemap.ui,
      markers: this.$bluemap.mapViewer.markers.data,
      controls: this.$bluemap.mapViewer.controlsManager.data,
      open: false,
    }
  },
  computed: {
    playerSet() {
      this.markers.markerSets.length;
      return this.markers.markerSets.find(set => set.id === "bm-players") || null;
    },
    hasPlayerSet() {
      return !!this.playerSet;
    },
    players() {
      this.ui.state.tick;
      this.controls.controls;
      return this.playerSet ? [...this.playerSet.markers].sort((a, b) => String(a.name).localeCompare(String(b.name))) : [];
    }
  },
  mounted() {
    window.addEventListener("pointerdown", this.outside, true);
  },
  beforeUnmount() {
    window.removeEventListener("pointerdown", this.outside, true);
  },
  methods: {
    outside(evt) {
      if (this.open && this.$refs.root && !this.$refs.root.contains(evt.target)) this.open = false;
    },
    where(player) {
      if (player.foreign) return this.$t("ui.player.otherWorld");
      const address = this.ui.addressAt(player.position.x, player.position.z);
      return address ? address.label : this.ui.mapName();
    },
    openPlayer(player) {
      this.open = false;
      this.ui.openPlayer(player);
      this.ui.focusPlayer(player);
    }
  }
}
</script>

<style lang="scss">
@import "/src/scss/variables.scss";

.bm-ui-topright {
  position: absolute;
  top: 12px;
  right: 16px;
  z-index: 26;
  pointer-events: auto;

  @media (max-width: $ui-mobile-break) {
    top: calc(112px + env(safe-area-inset-top, 0px));
    right: 12px;
  }
}

.bm-ui-online-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 6px 0 12px;
  border-radius: 20px;
  border: 0;
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  cursor: pointer;
  font: 500 13px var(--ui-font);
  color: var(--ui-text);

  @media (max-width: $ui-mobile-break) {
    padding: 0 4px;

    .bm-ui-online-label, .bm-ui-online-icon {
  color: var(--ui-text-2);
  margin: 0 4px;
}

.bm-ui-online-dot {
      display: none;
    }
  }
}

.bm-ui-online-icon {
  color: var(--ui-text-2);
  margin: 0 4px;
}

.bm-ui-online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #34a853;
}

.bm-ui-avatars {
  display: flex;

  img {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 2px solid var(--ui-surface);
    image-rendering: pixelated;
    margin-left: -8px;
    background: var(--ui-surface-2);

    &:first-child {
      margin-left: 0;
    }
  }
}

.bm-ui-online-pop {
  position: absolute;
  top: 48px;
  right: 0;
  width: 320px;
  max-width: calc(100vw - 24px);
  max-height: 60vh;
  overflow: auto;
  background: var(--ui-surface);
  color: var(--ui-text);
  border-radius: 12px;
  box-shadow: var(--ui-shadow);
  padding: 8px 0;

  h3 {
    margin: 0;
    padding: 8px 16px;
    font-size: 14px;
    font-weight: 500;
  }

  .bm-ui-row.compact {
    padding: 8px 16px;
    border-bottom: 0;

    .bm-ui-row-ico, .bm-ui-row-ico img {
      width: 32px;
      height: 32px;
    }

    .bm-ui-row-title {
      font-size: 14px;
    }
  }
}

.bm-ui-online-empty {
  margin: 0;
  padding: 4px 16px 12px;
  font-size: 13px;
  color: var(--ui-text-2);
}
</style>
