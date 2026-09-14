<vbox class="page">
  <PageHeader
    title={$t`Appearance`}
    subtitle={$t`Set the default way to display messages from this mailbox`} />

  <HeaderGroupBox>
    <hbox slot="header">{$t`Message viewer`}</hbox>
    <hbox class="subtitle">
      {$t`This setting applies to this mailbox only. External content can let a sender know that you opened a message.`}
    </hbox>
    <MessageContentRenderingOptions setting={messageContentRenderingSetting} />
  </HeaderGroupBox>
</vbox>

<script lang="ts">
  import type { MailAccount } from "../../../../logic/Mail/MailAccount";
  import HeaderGroupBox from "../../../Shared/HeaderGroupBox.svelte";
  import PageHeader from "../../Shared/PageHeader.svelte";
  import MessageContentRenderingOptions from "../MessageContentRenderingOptions.svelte";
  import { getMessageContentRenderingSetting } from "../../../Mail/Message/messageViewerAppearance";
  import { t } from "../../../../l10n/l10n";

  export let account: MailAccount;

  $: messageContentRenderingSetting = getMessageContentRenderingSetting(account);
</script>

<style>
  .page {
    max-width: 40em;
  }
  .subtitle {
    margin-block-end: 16px;
  }
</style>
