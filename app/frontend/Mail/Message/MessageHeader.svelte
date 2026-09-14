<vbox class="message-header"
  class:outgoing={$message.outgoing}
  on:swipeleft={onPreviousMessage}
  on:swiperight={onNextMessage}
  >
  <hbox class="subject-line">
    <value class="subject">{$message.subject}</value>
    <hbox flex />
    <value class="date font-small" title={$message.sent?.toLocaleString(getDateTimeLocale())}>
      {getDateTimeString($message.sent)}
    </value>
    {#if !$appGlobal.isSmall}
      <MessageZoomControls />
      <vbox class="display-mode">
        <DisplayModeSwitcher {message} />
      </vbox>
    {/if}
  </hbox>
  <ShowReplyBanner {message} />
  <hbox>
    {#if $message.contact instanceof Person && $message.contact.picture}
      <PersonPicture person={$message.contact} />
    {/if}
    <hbox class="from">
      {#if $message.outgoing && !$message.folder?.account?.isDependentAccount}
        <value class="from" title={$message.from.emailAddress}>
          {$t`me *=> myself as sender of the email`}
          {#if $message.from.emailAddress}
            <span class="sender-email">&lt;{$message.from.emailAddress}&gt;</span>
          {/if}
        </value>
      {:else}
        <Recipient recipient={$message.from} showFullEmail={true} />
      {/if}
      <EncryptionButtons {message} bind:isExpanded={isEncryptionExpanded} />
    </hbox>
    <hbox flex />
    <vbox class="top-right">
      <MessageToolbar {message} />
      <hbox>
        {#if $tags.hasItems}
          <hbox class="tags">
            <TagSelector tags={$tags} object={message} canAdd={false}>
              <RoundButton
                slot="tag-button"
                let:tag
                label={$t`Remove`}
                onClick={() => onTagRemove(tag)}
                icon={RemoveIcon}
                classes="small remove"
                iconSize="12px"
                padding="0px"
                border={false}
                />
            </TagSelector>
          </hbox>
        {/if}
      </hbox>
    </vbox>
  </hbox>
  {#if isEncryptionExpanded}
    <EncryptionDetails {message} bind:isExpanded={isEncryptionExpanded} />
  {/if}
  <vbox class="recipients">
    {#if $message.to.hasItems}
      <hbox class="to font-small">
        <hbox class="label">{$t`to`}</hbox>
        <RecipientsList recipients={$message.to} />
      </hbox>
    {/if}
    {#if $message.cc.hasItems}
      <hbox class="cc font-small">
        <hbox class="label">{$t`cc`}</hbox>
        <RecipientsList recipients={$message.cc} />
      </hbox>
    {/if}
    {#if $message.bcc.hasItems}
      <hbox class="bcc font-small">
        <hbox class="label">{$t`bcc`}</hbox>
        <RecipientsList recipients={$message.bcc} />
      </hbox>
    {/if}
  </vbox>
  {#if message.to.isEmpty || message.from.emailAddress == kDummyPerson.emailAddress}
  {#await message.loadForDisplay()}
    <!-- Subject etc. are loaded by search,
      and body is loaded by MessageBody calling message.loadBody(),
      but not to/from etc. -->
  {:catch ex}
    <ErrorMessageInline {ex} />
  {/await}
{/if}
</vbox>

<script lang="ts">
  import type { EMail } from "../../../logic/Mail/EMail";
  import { PersonUID, kDummyPerson } from "../../../logic/Abstract/PersonUID";
  import { Person } from "../../../logic/Abstract/Person";
  import type { PersonOrGroup } from "../../Contacts/Person/PersonOrGroup";
  import { selectedPerson } from "../../Contacts/Person/Selected";
  import type { Tag } from "../../../logic/Abstract/Tag";
  import { appGlobal } from "../../../logic/app";
  import MessageToolbar from "./MessageToolbar.svelte";
  import RecipientsList from "./RecipientsList.svelte";
  import Recipient from "./Recipient.svelte";
  import PersonPicture from "../../Contacts/Person/PersonPicture.svelte";
  import DisplayModeSwitcher from "./DisplayModeSwitcher.svelte";
  import MessageZoomControls from "./MessageZoomControls.svelte";
  import TagSelector from "../../Shared/Tag/TagSelector.svelte";
  import EncryptionButtons from "./EncryptionButtons.svelte";
  import EncryptionDetails from "./EncryptionDetails.svelte";
  import ErrorMessageInline from "../../Shared/ErrorMessageInline.svelte";
  import ShowReplyBanner from "./ShowReplyBanner.svelte";
  import RoundButton from "../../Shared/RoundButton.svelte";
  import RemoveIcon from "lucide-svelte/icons/x";
  import { getLocalStorage } from "../../Util/LocalStorage";
  import { catchErrors, backgroundError } from "../../Util/error";
  import { getDateTimeString } from "../../Util/date";
  import { getDateTimeLocale, t } from "../../../l10n/l10n";
  import { onDestroy } from "svelte";

  export let message: EMail;

  $: tags = message.tags;

  let readDelaySetting = getLocalStorage("mail.read.after", 0); // 0 = Immediately; -1 = Manually; 1 to 20 = delay in seconds
  $: readDelay = $readDelaySetting.value;
  $: catchErrors(() => markMessageAsRead(message, readDelay), backgroundError);
  let readTimeout: NodeJS.Timeout;
  function markMessageAsRead(message: EMail, readDelay: number) {
    if (message.isRead) {
      return;
    }
    if (readDelay < 0) {
      return;
    }
    if (readDelay == 0) {
      readDelay = 0.2; // Avoid that normal scrolling marks all msgs as read
    }
    clearTimeout(readTimeout);
    readTimeout = setTimeout(() => {
      message.markRead(true)
        .catch(message.folder.account.errorCallback);
    }, readDelay * 1000);
  }
  onDestroy(() => {
    clearTimeout(readTimeout);
  });

  let isEncryptionExpanded = false;
  $: $message, closeEncryption()
  function closeEncryption() {
    isEncryptionExpanded = false;
  }

  // TODO Duplicated in MailApp.svelte
  $: selectPerson(message?.contact);
  function selectPerson(contact: PersonOrGroup | PersonUID) {
    if (contact instanceof PersonUID) {
      contact = contact.findPerson();
    }
    if (!(contact instanceof Person)) {
      return;
    }
    $selectedPerson = contact;
  }

  async function onTagRemove(tag: Tag) {
    await message.removeTag(tag);
  }

  function onNextMessage() {
    message = message.nextMessage(false);
  }
  function onPreviousMessage() {
    message = message.nextMessage(true);
  }
</script>

<style>
  .message-header {
    min-height: 5em;
    padding: 16px 24px 12px;
    background-color: transparent;
    border-block-end: 1px solid var(--border);
    z-index: 1;
  }
  .message-header > hbox:not(.subject-line) {
    align-items: center;
  }
  .top-right {
    align-items: end;
  }
  .tags {
    margin-inline-end: 12px;
  }
  .subject {
    font-weight: 700;
    font-size: 15px;
    letter-spacing: -0.015em;
  }
  .from {
    font-weight: bold;
  }
  .sender-email {
    font-weight: normal;
    margin-inline-start: 4px;
  }
  .from :global(.domain) {
    font-weight: normal;
  }
  .outgoing .from {
    font-weight: normal;
    color: color-mix(in srgb, var(--message-viewer-fg, var(--main-fg)) 68%, transparent);
  }
  .recipients {
    justify-content: end;
  }
  .recipients .label {
    margin-block-start: 2px;
    margin-inline-end: 6px;
  }
  .to {
    color: color-mix(in srgb, var(--message-viewer-fg, var(--main-fg)) 68%, transparent);
  }
  .cc, .bcc {
    color: color-mix(in srgb, var(--message-viewer-fg, var(--main-fg)) 68%, transparent);
  }
  .outgoing .to {
    font-weight: bold;
    color: inherit;
  }
  .date {
    align-self: center;
    margin-inline-end: 16px;
    font-weight: 300;
  }
  .subject-line {
    flex-wrap: wrap;
    justify-content: end;
    align-items: center;
    min-height: 28px;
    margin-block-end: 10px;
  }
  .display-mode {
    justify-content: end;
  }
  .message-header :global(.error) {
    margin-inline: -4px -12px;
  }
  @media (max-width: 600px)  {
    .message-header {
      min-height: 0;
      padding: 10px 8px 8px 16px;
    }
    .display-mode {
      display: none;
    }
    .date {
      margin-inline-end: 6px;
    }
  }
</style>
