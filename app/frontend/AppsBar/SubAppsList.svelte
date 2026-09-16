{#if $subApps.hasItems}
  <!-- A list of parts of the app,
    shown in the AppBar underneath the app button -->
  <hbox class="sub-app-bar" app={mainApp.id}>
    {#each $subApps.each as app}
      <SubAppButton selected={selectedApp == app} {app} minimized={isComposeMinimized(app, $floatingComposes)}
        on:click={() => catchErrors(() => onSelectApp(app))}>
        <AppIcon slot="icon" icon={app.icon} size="16px" />
      </SubAppButton>
    {/each}
  </hbox>
{/if}

<script lang="ts">
  import type { JackdawApp } from "./JackdawApp";
  import { bringAppToFront, openApp } from "./selectedApp";
  import SubAppButton from "./SubAppButton.svelte";
  import AppIcon from "./AppIcon.svelte";
  import { catchErrors } from "../Util/error";
  import { CollectionObserver } from "svelte-collections";
  import { onDestroy, onMount } from "svelte";
  import { WriteMailJackdawApp } from "../Mail/MailJackdawApp";
  import {
    floatingComposes,
    focusFloatingCompose,
    openFloatingCompose,
    shouldOpenComposeInWindow,
    type FloatingComposeEntry,
  } from "../Mail/Composer/composeFloating";
  import { focusNativeComposeWindow, supportsNativeComposeWindow } from "../Mail/Composer/composeNative";
  import type { EMail } from "../../logic/Mail/EMail";

  export let mainApp: JackdawApp;
  /* in/out */
  export let selectedApp: JackdawApp | null;

  $: subApps = $mainApp.subApps;

  function isComposeMinimized(app: JackdawApp, entries: FloatingComposeEntry[]): boolean {
    if (!(app instanceof WriteMailJackdawApp) || !shouldOpenComposeInWindow()) {
      return false;
    }
    if (supportsNativeComposeWindow()) {
      return false;
    }
    let mail = app.windowParams?.mail as EMail | undefined;
    if (!mail) {
      return false;
    }
    return entries.find(entry => entry.mail === mail)?.minimized ?? false;
  }

  function onSelectApp(app: JackdawApp) {
    if (app instanceof WriteMailJackdawApp && shouldOpenComposeInWindow()) {
      if (supportsNativeComposeWindow()) {
        void focusNativeComposeWindow(app, app.windowParams.mail)
          .catch(() => {
            openFloatingCompose(app, app.windowParams.mail);
            bringAppToFront();
          });
      } else {
        focusFloatingCompose(app.windowParams.mail);
        bringAppToFront();
      }
      return;
    }
    openApp(app, app.windowParams);
  }

  // Unselect sub-app that has been removed
  class RemovalObserver extends CollectionObserver<JackdawApp> {
    added(apps: JackdawApp[]) {}
    removed(apps: JackdawApp[]) {
      for (let app of apps) {
        if (selectedApp == app) {
          selectedApp = mainApp.subApps.last ?? mainApp;
        }
      }
    }
  }
  let removalObserver = new RemovalObserver();
  onMount(() => catchErrors(() => {
    subApps.registerObserver(removalObserver);
  }));
  onDestroy(() => catchErrors(() => {
    subApps.unregisterObserver(removalObserver);
  }));
</script>

<style>
  .sub-app-bar {
    justify-content: end;
    flex-wrap: wrap;
    margin: 0px 12px 2px 6px;
  }
  .sub-app-bar[app="webapps"] {
    margin-block-start: 8px;
  }
</style>
