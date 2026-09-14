<vbox class="mode-options" role="radiogroup" aria-label={$t`Message display mode`}>
  <label class="radio">
    <input
      type="radio"
      name="message-content-rendering"
      value="html"
      checked={mode == "html"}
      on:change={() => selectMode("html")}
      />
    {$t`Formatted`}
  </label>
  <label class="radio">
    <input
      type="radio"
      name="message-content-rendering"
      value="with-external"
      checked={mode == "with-external"}
      on:change={() => selectMode("with-external")}
      />
    {$t`With external content (allows sender to track you)`}
  </label>
  <label class="radio">
    <input
      type="radio"
      name="message-content-rendering"
      value="plaintext"
      checked={mode == "plaintext"}
      on:change={() => selectMode("plaintext")}
      />
    {$t`Plaintext`}
  </label>
</vbox>

<script lang="ts">
  import {
    normalizeMessageContentRendering,
    type MessageContentRendering,
  } from "../../Mail/Message/messageViewerAppearance";
  import { t } from "../../../l10n/l10n";

  export let setting: { value: MessageContentRendering };

  $: mode = normalizeMessageContentRendering($setting.value);

  function selectMode(newMode: MessageContentRendering): void {
    setting.value = newMode;
  }
</script>

<style>
  .mode-options {
    gap: 8px;
  }
  .radio {
    align-items: center;
    gap: 8px;
  }
  .radio:focus-within {
    outline: 2px solid color-mix(in srgb, var(--input-focus) 68%, transparent);
    outline-offset: 2px;
  }
</style>
