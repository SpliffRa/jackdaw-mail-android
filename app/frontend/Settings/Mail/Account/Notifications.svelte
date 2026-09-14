<vbox class="page">
  <PageHeader
    title={$t`Notifications`}
    subtitle={$t`Configure notifications for this mailbox`} />

  <HeaderGroupBox>
    <hbox slot="header">{$t`New mail`}</hbox>
    <label class="checkbox-row">
      <input
        type="checkbox"
        checked={notificationSettings.enabled}
        on:change={onEnabledChange}
        />
      {$t`Notify me about new mail in this mailbox`}
    </label>
    <span class="hint">
      {$t`The notification includes this mailbox name, so you can tell which account received the message.`}
    </span>
  </HeaderGroupBox>

  <HeaderGroupBox>
    <hbox slot="header">{$t`Notification sound`}</hbox>
    <hbox class="subtitle">
      {$t`Choose a sound for incoming mail in this mailbox. This overrides the global mail sound.`}
    </hbox>
    <hbox class="sound-row">
      <label for="mail-account-notification-sound">{$t`Sound`}</label>
      <select
        id="mail-account-notification-sound"
        value={notificationSettings.sound}
        on:change={onSoundChange}>
        <option value="global">{$t`Use global mail sound`}</option>
        {#each notificationSoundOptions as sound}
          <option value={sound}>{soundLabel(sound)}</option>
        {/each}
        {#if notificationSettings.customSoundDataURL}
          <option value="custom">{customSoundLabel()}</option>
        {/if}
      </select>
      <Button
        label={$t`Preview`}
        icon={VolumeIcon}
        onClick={previewSound}
        />
    </hbox>

    <hbox class="file-actions">
      <input
        bind:this={fileInput}
        class="file-input"
        type="file"
        accept="audio/*"
        aria-label={$t`Choose a custom notification sound`}
        on:change={onFileSelected}
        />
      <Button
        label={$t`Choose custom sound`}
        icon={UploadIcon}
        onClick={chooseFile}
        />
      {#if notificationSettings.customSoundDataURL}
        <span class="file-name">{customSoundLabel()}</span>
        <Button
          label={$t`Remove custom sound`}
          icon={DeleteIcon}
          plain={true}
          onClick={removeCustomSound}
          />
      {/if}
    </hbox>
    <span class="hint">{$t`Audio files up to 2 MB are stored locally on this device.`}</span>
    {#if uploadError}
      <p class="error" role="alert">{uploadError}</p>
    {/if}
  </HeaderGroupBox>
</vbox>

<script lang="ts">
  import type { MailAccount } from "../../../../logic/Mail/MailAccount";
  import { blobToDataURL } from "../../../../logic/util/util";
  import { t } from "../../../../l10n/l10n";
  import Button from "../../../Shared/Button.svelte";
  import DeleteIcon from "lucide-svelte/icons/trash-2";
  import UploadIcon from "lucide-svelte/icons/upload";
  import VolumeIcon from "lucide-svelte/icons/volume-2";
  import HeaderGroupBox from "../../../Shared/HeaderGroupBox.svelte";
  import PageHeader from "../../Shared/PageHeader.svelte";
  import {
    getMailAccountNotificationSetting,
    getMailNotificationSound,
    kMaxCustomNotificationSoundBytes,
    readMailAccountNotificationSettings,
    updateMailAccountNotificationSettings,
  } from "../../../Mail/mailNotificationSettings";
  import {
    isNotificationSoundId,
    notificationSoundOptions,
    playNotificationSound,
    type NotificationSoundId,
  } from "../../../Shared/NotificationSound";

  export let account: MailAccount;

  let setting = getMailAccountNotificationSetting(account);
  $: setting = getMailAccountNotificationSetting(account);
  $: notificationSettings = readMailAccountNotificationSettings($setting.value);

  let fileInput: HTMLInputElement;
  let uploadError: string | null = null;

  function onEnabledChange(event: Event): void {
    updateMailAccountNotificationSettings(account, {
      enabled: (event.currentTarget as HTMLInputElement).checked,
    });
  }

  function onSoundChange(event: Event): void {
    let value = (event.currentTarget as HTMLSelectElement).value;
    if (value == "global" || value == "custom" || isNotificationSoundId(value)) {
      updateMailAccountNotificationSettings(account, { sound: value });
    }
  }

  function chooseFile(): void {
    fileInput?.click();
  }

  async function onFileSelected(event: Event): Promise<void> {
    uploadError = null;
    let input = event.currentTarget as HTMLInputElement;
    let file = input.files?.[0];
    input.value = "";
    if (!file) {
      return;
    }
    if (!file.type.startsWith("audio/")) {
      uploadError = $t`Please choose an audio file.`;
      return;
    }
    if (!file.size || file.size > kMaxCustomNotificationSoundBytes) {
      uploadError = $t`The audio file must be smaller than 2 MB.`;
      return;
    }
    try {
      let dataURL = await blobToDataURL(file);
      if (!dataURL.startsWith("data:audio/")) {
        throw new Error("Audio data URL has an unsupported format");
      }
      updateMailAccountNotificationSettings(account, {
        sound: "custom",
        customSoundDataURL: dataURL,
        customSoundName: file.name.slice(0, 120),
      });
    } catch (_ex) {
      uploadError = $t`Could not read the audio file.`;
    }
  }

  function removeCustomSound(): void {
    updateMailAccountNotificationSettings(account, {
      sound: notificationSettings.sound == "custom" ? "global" : notificationSettings.sound,
      customSoundDataURL: null,
      customSoundName: null,
    });
  }

  async function previewSound(): Promise<void> {
    await playNotificationSound("mail-incoming", {
      preview: true,
      sound: getMailNotificationSound(account),
    });
  }

  function soundLabel(sound: NotificationSoundId): string {
    switch (sound) {
      case "none": return $t`Off`;
      case "default": return $t`Classic`;
      case "chime": return $t`Chime`;
      case "pop": return $t`Pop`;
      case "bell": return $t`Bell`;
      case "alarm": return $t`Alarm`;
    }
  }

  function customSoundLabel(): string {
    return $t`Custom: ${notificationSettings.customSoundName ?? $t`Audio file`}`;
  }
</script>

<style>
  .page {
    max-width: 48em;
  }
  .subtitle {
    margin-block-end: 16px;
  }
  .checkbox-row,
  .sound-row,
  .file-actions {
    align-items: center;
    gap: 8px;
  }
  .hint {
    color: var(--input-placeholder);
    display: block;
    font-size: 13px;
    line-height: 1.4;
    margin-block-start: 8px;
  }
  .sound-row {
    flex-wrap: wrap;
  }
  .sound-row label {
    min-width: 4em;
  }
  select {
    min-width: 200px;
  }
  .file-actions {
    flex-wrap: wrap;
    margin-block-start: 20px;
  }
  .file-input {
    height: 1px;
    opacity: 0;
    position: absolute;
    width: 1px;
  }
  .file-name {
    color: var(--input-placeholder);
    max-width: 22em;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .error {
    color: var(--error-fg, #c62828);
    margin-block: 12px 0;
  }
</style>
