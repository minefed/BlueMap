<template>
  <div class="bm-ui-settings">
    <div class="bm-ui-panel-spacer"></div>
    <div class="bm-ui-list-title"><h1>{{ $t("settings.title") }}</h1></div>

    <section class="bm-ui-set-group">
      <h3>{{ $t("controls.title") }}</h3>
      <div class="bm-ui-seg">
        <button :class="{on: appState.controls.state === 'perspective'}" @click="setView('perspective')">{{ $t("controls.perspective.button") }}</button>
        <button :class="{on: appState.controls.state === 'flat'}" @click="setView('flat')">{{ $t("controls.flatView.button") }}</button>
        <button v-if="appState.controls.enableFreeFlight" :class="{on: appState.controls.state === 'free'}" @click="setView('free')">{{ $t("controls.freeFlight.button") }}</button>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("lighting.title") }}</h3>
      <div class="bm-ui-set-row">
        <label for="bm-set-sun">{{ $t("lighting.sunlight") }}</label>
        <input id="bm-set-sun" type="range" min="0" max="1" step="0.01" :value="mapViewer.uniforms.sunlightStrength.value"
               @input="mapViewer.uniforms.sunlightStrength.value = +$event.target.value; $bluemap.mapViewer.redraw()">
        <output>{{ Math.round(mapViewer.uniforms.sunlightStrength.value * 100) }}%</output>
      </div>
      <div class="bm-ui-set-row">
        <label for="bm-set-ambient">{{ $t("lighting.ambientLight") }}</label>
        <input id="bm-set-ambient" type="range" min="0" max="1" step="0.01" :value="mapViewer.uniforms.ambientLight.value"
               @input="mapViewer.uniforms.ambientLight.value = +$event.target.value; $bluemap.mapViewer.redraw()">
        <output>{{ Math.round(mapViewer.uniforms.ambientLight.value * 100) }}%</output>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("resolution.title") }}</h3>
      <div class="bm-ui-seg">
        <button v-for="stage of qualityStages" :key="stage.value" :class="{on: mapViewer.superSampling === stage.value}" :title="stage.name"
                @click="$bluemap.mapViewer.superSampling = stage.value; $bluemap.saveUserSettings(); $bluemap.mapViewer.redraw()">{{ stage.short }}</button>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("renderDistance.title") }}</h3>
      <div class="bm-ui-set-row">
        <label for="bm-set-hires">{{ $t("renderDistance.hiresLayer") }}</label>
        <input id="bm-set-hires" type="range" :min="settings.hiresSliderMin" :max="settings.hiresSliderMax" step="10" :value="mapViewer.loadedHiresViewDistance"
               @input="mapViewer.loadedHiresViewDistance = +$event.target.value; $bluemap.mapViewer.updateLoadedMapArea()" @change="$bluemap.saveUserSettings()">
        <output>{{ renderDistance(mapViewer.loadedHiresViewDistance) }}</output>
      </div>
      <div class="bm-ui-set-row">
        <label for="bm-set-lowres">{{ $t("renderDistance.lowersLayer") }}</label>
        <input id="bm-set-lowres" type="range" :min="settings.lowresSliderMin" :max="settings.lowresSliderMax" step="100" :value="mapViewer.loadedLowresViewDistance"
               @input="mapViewer.loadedLowresViewDistance = +$event.target.value; $bluemap.mapViewer.updateLoadedMapArea()" @change="$bluemap.saveUserSettings()">
        <output>{{ renderDistance(mapViewer.loadedLowresViewDistance) }}</output>
      </div>
      <div class="bm-ui-set-row">
        <span>{{ $t("renderDistance.loadHiresWhileMoving") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(!appState.controls.pauseTileLoading)" :aria-label="$t('renderDistance.loadHiresWhileMoving')"
                @click="appState.controls.pauseTileLoading = !appState.controls.pauseTileLoading; $bluemap.saveUserSettings()"><span></span></button>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("mapControls.title") }}</h3>
      <div class="bm-ui-set-row">
        <span>{{ $t("mapControls.showZoomButtons") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(appState.controls.showZoomButtons)" :aria-label="$t('mapControls.showZoomButtons')"
                @click="appState.controls.showZoomButtons = !appState.controls.showZoomButtons; $bluemap.saveUserSettings()"><span></span></button>
      </div>
    </section>

    <section class="bm-ui-set-group" v-if="appState.controls.enableFreeFlight">
      <h3>{{ $t("freeFlightControls.title") }}</h3>
      <div class="bm-ui-set-row">
        <label for="bm-set-sensitivity">{{ $t("freeFlightControls.mouseSensitivity") }}</label>
        <input id="bm-set-sensitivity" type="range" min="0.1" max="5" step="0.05" :value="appState.controls.mouseSensitivity"
               @input="appState.controls.mouseSensitivity = +$event.target.value; $bluemap.updateControlsSettings()" @change="$bluemap.saveUserSettings()">
        <output>{{ appState.controls.mouseSensitivity.toFixed(2) }}</output>
      </div>
      <div class="bm-ui-set-row">
        <span>{{ $t("freeFlightControls.invertMouseY") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(appState.controls.invertMouse)" :aria-label="$t('freeFlightControls.invertMouseY')"
                @click="appState.controls.invertMouse = !appState.controls.invertMouse; $bluemap.updateControlsSettings(); $bluemap.saveUserSettings()"><span></span></button>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("theme.title") }}</h3>
      <div class="bm-ui-seg">
        <button v-for="theme of themes" :key="String(theme.value)" :class="{on: appState.theme === theme.value}"
                @click="$bluemap.setTheme(theme.value); $bluemap.saveUserSettings()">{{ theme.name }}</button>
      </div>
    </section>

    <section class="bm-ui-set-group" v-if="languages.length > 1">
      <h3>{{ $t("language.title") }}</h3>
      <div class="bm-ui-set-row">
        <label for="bm-set-language">{{ $t("language.title") }}</label>
        <select id="bm-set-language" :value="$i18n.locale" @change="changeLanguage($event.target.value)">
          <option v-for="lang of languages" :key="lang.locale" :value="lang.locale">{{ lang.name }}</option>
        </select>
      </div>
    </section>

    <section class="bm-ui-set-group">
      <h3>{{ $t("ui.settings.other") }}</h3>
      <div class="bm-ui-set-row">
        <span>{{ $t("chunkBorders.button") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(mapViewer.uniforms.chunkBorders.value)" :aria-label="$t('chunkBorders.button')"
                @click="$bluemap.setChunkBorders(!mapViewer.uniforms.chunkBorders.value); $bluemap.saveUserSettings(); $bluemap.mapViewer.redraw()"><span></span></button>
      </div>
      <div class="bm-ui-set-row">
        <span>{{ $t("screenshot.clipboard") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(appState.screenshot.clipboard)" :aria-label="$t('screenshot.clipboard')"
                @click="appState.screenshot.clipboard = !appState.screenshot.clipboard; $bluemap.saveUserSettings()"><span></span></button>
      </div>
      <div class="bm-ui-set-row">
        <span>{{ $t("debug.button") }}</span>
        <button class="bm-ui-switch" role="switch" :aria-checked="String(appState.debug)" :aria-label="$t('debug.button')"
                @click="$bluemap.setDebug(!appState.debug); $bluemap.saveUserSettings()"><span></span></button>
      </div>
      <button class="bm-ui-text-btn" @click="$bluemap.resetSettings()">{{ $t("resetAllSettings.button") }}</button>
    </section>
  </div>
</template>

<script>
import {i18n, setLanguage} from "../../i18n";

export default {
  name: "SettingsView",
  data() {
    return {
      appState: this.$bluemap.appState,
      mapViewer: this.$bluemap.mapViewer.data,
      settings: {
        hiresSliderMax: 500,
        hiresSliderMin: 50,
        lowresSliderMax: 10000,
        lowresSliderMin: 500,
        ...this.$bluemap.settings,
      },
      languages: i18n.languages || [],
    }
  },
  computed: {
    themes() {
      return [
        {name: this.$t("ui.settings.themeSystem"), value: null},
        {name: this.$t("theme.light"), value: "light"},
        {name: this.$t("theme.dark"), value: "dark"},
        {name: this.$t("theme.contrast"), value: "contrast"},
      ];
    },
    qualityStages() {
      return [
        {name: this.$t("resolution.high"), short: this.$t("ui.settings.qualityHigh"), value: 2},
        {name: this.$t("resolution.normal"), short: this.$t("ui.settings.qualityNormal"), value: 1},
        {name: this.$t("resolution.low"), short: this.$t("ui.settings.qualityLow"), value: 0.5},
      ];
    }
  },
  methods: {
    setView(mode) {
      const fromFree = this.appState.controls.state === "free" ? 100 : 0;
      if (mode === "perspective") this.$bluemap.setPerspectiveView(500, fromFree);
      else if (mode === "flat") this.$bluemap.setFlatView(500, fromFree);
      else this.$bluemap.setFreeFlight(500);
    },
    renderDistance(value) {
      const f = parseFloat(value);
      return f === 0 ? this.$t("renderDistance.off") : f.toFixed(0);
    },
    async changeLanguage(lang) {
      await setLanguage(lang);
      this.$bluemap.saveUserSettings();
    }
  }
}
</script>

<style lang="scss">
.bm-ui-set-group {
  padding: 12px 24px 16px;
  border-bottom: 1px solid var(--ui-line);

  h3 {
    margin: 0 0 10px;
    padding: 0;
    font-size: 13px;
    font-weight: 500;
    color: var(--ui-text-2);
    letter-spacing: .02em;
  }
}

.bm-ui-seg {
  display: flex;
  border: 1px solid var(--ui-line);
  border-radius: 18px;
  overflow: hidden;

  button {
    flex: 1;
    min-height: 34px;
    border: 0;
    border-left: 1px solid var(--ui-line);
    background: none;
    cursor: pointer;
    font: 400 13px var(--ui-font);
    color: var(--ui-text);
    padding: 4px 6px;

    &:first-child {
      border-left: 0;
    }

    &.on {
      background: var(--ui-accent-weak);
      color: var(--ui-accent-text);
      font-weight: 500;
    }
  }
}

.bm-ui-set-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 40px;
  font-size: 14px;

  label, > span {
    flex: 1;
  }

  input[type=range] {
    flex: 1.2;
    min-width: 0;
    accent-color: var(--ui-accent);
  }

  output {
    font-family: var(--ui-font-mono);
    font-size: 12px;
    color: var(--ui-text-2);
    min-width: 44px;
    text-align: right;
  }

  select {
    font: 400 14px var(--ui-font);
    color: var(--ui-text);
    background: var(--ui-surface);
    border: 1px solid var(--ui-line);
    border-radius: 8px;
    height: 36px;
    padding: 0 8px;
  }
}

.bm-ui-switch {
  width: 36px;
  height: 20px;
  border-radius: 10px;
  border: 0;
  background: var(--ui-surface-3);
  position: relative;
  cursor: pointer;
  flex: none;
  padding: 0;

  span {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--ui-text-3);
    transition: left .15s, background .15s;
  }

  &[aria-checked="true"] {
    background: var(--ui-accent-weak);

    span {
      left: 18px;
      background: var(--ui-accent);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    span {
      transition: none;
    }
  }
}

.bm-ui-text-btn {
  border: 0;
  background: none;
  color: var(--ui-accent-text);
  font: 500 14px var(--ui-font);
  cursor: pointer;
  padding: 8px 0;
}
</style>
