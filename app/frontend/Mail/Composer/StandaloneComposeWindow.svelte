<vbox flex class="standalone-compose">
  <NotificationBar notifications={$notifications} />
  {#if loading}
    <vbox flex class="compose-state" aria-live="polite">
      <Spinner size="36px" />
      <p>{$t`Loading compose window…`}</p>
    </vbox>
  {:else if errorMessage}
    <vbox flex class="compose-state" role="alert">
      <p>{errorMessage}</p>
      <button type="button" on:click={load}>{$t`Retry`}</button>
    </vbox>
  {:else if mail}
    <MailComposer {mail} standalone on:close={closeWindow} />
  {/if}
</vbox>

<script lang="ts">
  import { onMount } from "svelte";
  import type { EMail } from "../../../logic/Mail/EMail";
  import { appGlobal } from "../../../logic/app";
  import { getStartObjects } from "../../../logic/startup";
  import { assert } from "../../../logic/util/util";
  import { deserializeComposeMail } from "./composeWindow";
  import MailComposer from "./MailComposer.svelte";
  import NotificationBar from "../../MainWindow/NotificationBar.svelte";
  import { notifications } from "../../MainWindow/Notification";
  import Spinner from "../../Shared/Spinner.svelte";
  import { t } from "../../../l10n/l10n";

  export let composeWindowID: string;

  let mail: EMail | null = null;
  let loading = true;
  let errorMessage: string | null = null;
  let startObjectsPromise: Promise<void> | null = null;

  async function load(): Promise<void> {
    loading = true;
    errorMessage = null;
    mail = null;
    try {
      let nativeAPI = (window as any).api;
      if (typeof nativeAPI?.getComposeWindowData !== "function") {
        throw new Error("Native compose windows are unavailable");
      }
      let payload = await nativeAPI.getComposeWindowData(composeWindowID);
      assert(payload, "Compose window data is missing");
      startObjectsPromise ??= getStartObjects();
      try {
        await startObjectsPromise;
      } catch (ex) {
        startObjectsPromise = null;
        throw ex;
      }
      let account = appGlobal.emailAccounts.find(candidate => candidate.id === payload.accountID);
      assert(account, "Compose window account is missing");
      await account.readFromDB();
      mail = deserializeComposeMail(payload, account);
    } catch (ex) {
      errorMessage = ex instanceof Error ? ex.message : $t`Could not load the compose window`;
    } finally {
      loading = false;
    }
  }

  function closeWindow(): void {
    (window as any).api.closeComposeWindow(composeWindowID);
  }

  onMount(() => {
    void load();
  });
</script>

<style>
  .standalone-compose {
    min-width: 0;
    min-height: 100vh;
    background: var(--main-bg, var(--bg));
    color: var(--main-fg, var(--fg));
  }
  .compose-state {
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 32px;
    text-align: center;
  }
  .compose-state p {
    max-width: 560px;
    margin: 0;
  }
  .compose-state button {
    min-height: 34px;
    padding: 6px 16px;
    border: 1px solid var(--button-border);
    border-radius: var(--border-radius);
    background: var(--button-bg);
    color: var(--button-fg);
    cursor: default;
  }
  .compose-state button:hover,
  .compose-state button:focus-visible {
    background: var(--hover-bg);
    color: var(--hover-fg);
  }
</style>
