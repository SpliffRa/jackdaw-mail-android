<script lang="ts">
  import { onMount } from "svelte";
  import type { EMail } from "../../../logic/Mail/EMail";
  import { mailApp, WriteMailJackdawApp } from "../MailJackdawApp";
  import { openApp } from "../../AppsBar/selectedApp";
  import { focusFloatingCompose, floatingComposes, openFloatingCompose } from "./composeFloating";
  import { openNativeComposeWindow, supportsNativeComposeWindow } from "./composeNative";
  import { get } from "svelte/store";

  export let mail: EMail;

  onMount(() => {
    let existing = get(floatingComposes).find(entry => entry.mail === mail);
    if (!existing) {
      let app = mailApp.subApps.find(
        candidate => candidate instanceof WriteMailJackdawApp && candidate.windowParams?.mail === mail,
      ) as WriteMailJackdawApp | undefined;
      if (!app) {
        app = new WriteMailJackdawApp();
        app.windowParams = { mail };
        mailApp.subApps.add(app);
      }
      if (supportsNativeComposeWindow()) {
        void openNativeComposeWindow(app, mail)
          .catch(() => openFloatingCompose(app, mail));
      } else {
        openFloatingCompose(app, mail);
      }
    } else {
      if (supportsNativeComposeWindow()) {
        void openNativeComposeWindow(existing.app, mail)
          .catch(() => openFloatingCompose(existing.app, mail));
      } else {
        focusFloatingCompose(mail);
      }
    }
    openApp(mailApp, {});
  });
</script>
