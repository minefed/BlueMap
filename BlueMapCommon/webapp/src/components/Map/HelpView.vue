<template>
  <div class="bm-ui-help">
    <div class="bm-ui-panel-spacer"></div>
    <div class="bm-ui-list-title"><h1>{{ $t("info.title") }}</h1></div>
    <section class="bm-ui-set-group">
      <h3>{{ $t("ui.help.mapTitle") }}</h3>
      <table class="bm-ui-help-table">
        <tr><td>{{ $t("ui.help.search") }}</td><td><kbd>/</kbd></td></tr>
        <tr><td>{{ $t("ui.help.location") }}</td><td>{{ $t("ui.help.locationKeys") }}</td></tr>
        <tr><td>{{ $t("ui.help.menu") }}</td><td>{{ $t("ui.help.menuKeys") }}</td></tr>
        <tr><td>{{ $t("ui.help.close") }}</td><td><kbd>Esc</kbd></td></tr>
      </table>
    </section>
    <div class="bm-ui-help-content" v-html="$t('info.content', {version: $bluemap.settings.version})"></div>
  </div>
</template>

<script>
export default {
  name: "HelpView",
}
</script>

<style lang="scss">
.bm-ui-help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  td {
    padding: 8px 0;
    border-bottom: 1px solid var(--ui-line);
    vertical-align: top;

    &:first-child {
      color: var(--ui-text-2);
      width: 45%;
    }
  }
}

.bm-ui-help-content {
  padding: 0 24px 24px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ui-text);

  img {
    width: 72px !important;
    margin: 16px 0 !important;
  }

  h2 {
    font-size: 15px;
    font-weight: 500;
    padding: 0 0 6px;
    margin: 16px 0 6px;
    width: 100%;
    text-align: left;

    &:after {
      background-color: var(--ui-line);
    }
  }

  p {
    margin: 8px 0;
  }

  table {
    border-collapse: collapse;
    width: 100%;

    th, td {
      padding: 6px 0;
      border-bottom: 1px solid var(--ui-line);
      font-weight: inherit;
      text-align: left;
      vertical-align: top;
    }

    th {
      color: var(--ui-text-2);
      width: 45%;
    }
  }

  .info-footer {
    color: var(--ui-text-2);
    font-size: 12px;
  }
}
</style>
