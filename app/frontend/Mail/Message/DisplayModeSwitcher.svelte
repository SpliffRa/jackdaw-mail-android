<IslandSwitcher border={false}>
  <Button
    label={$t`Formatted`}
    icon={HTMLIcon}
    iconOnly
    iconSize="16px"
    onClick={() => switchTo(DisplayMode.HTML)}
    selected={mode == DisplayMode.HTML || mode == DisplayMode.Thread}
    />
  <Button
    label={$t`With external content (allows sender to track you)`}
    icon={WithExternalIcon}
    iconOnly
    iconSize="16px"
    onClick={() => switchTo(DisplayMode.HTMLWithExternal)}
    selected={mode == DisplayMode.HTMLWithExternal}
    />
  <Button
    label={$t`Plaintext`}
    icon={PlaintextIcon}
    iconOnly
    iconSize="16px"
    onClick={() => switchTo(DisplayMode.Plaintext)}
    selected={mode == DisplayMode.Plaintext}
    />
  {#if mode == DisplayMode.Source}
    <Button
      label={$t`Source`}
      icon={SourceIcon}
      iconOnly
      iconSize="16px"
      onClick={() => switchTo(DisplayMode.Source)}
      selected={mode == DisplayMode.Source}
      />
  {/if}
</IslandSwitcher>

<script lang="ts">
  import type { EMail } from "../../../logic/Mail/EMail";
  import { DisplayMode } from "./MessageBody.svelte";
  import {
    getMessageContentRenderingSetting,
    normalizeMessageContentRendering,
  } from "./messageViewerAppearance";
  import IslandSwitcher from "../../Shared/IslandSwitcher.svelte";
  import Button from "../../Shared/Button.svelte";
  import HTMLIcon from "lucide-svelte/icons/mail";
  import WithExternalIcon from "lucide-svelte/icons/image";
  import PlaintextIcon from "lucide-svelte/icons/type";
  import SourceIcon from "lucide-svelte/icons/code-xml";
  import { t } from "../../../l10n/l10n";

  export let message: EMail;
  export let mode: DisplayMode = DisplayMode.HTML;

  let messageAccount = message?.folder?.account;
  let modeSetting = getMessageContentRenderingSetting(messageAccount);
  $: messageAccount = $message?.folder?.account ?? message?.folder?.account;
  $: modeSetting = getMessageContentRenderingSetting(messageAccount);
  $: mode = normalizeMessageContentRendering($modeSetting.value) as DisplayMode;

  function switchTo(newMode: DisplayMode) {
    let normalizedMode = normalizeMessageContentRendering(newMode);
    mode = normalizedMode as DisplayMode;
    modeSetting.value = normalizedMode;
  }
</script>
