import type { EMail } from "../../logic/Mail/EMail";
import { JackdawApp } from "../AppsBar/JackdawApp";
import { openApp } from "../AppsBar/selectedApp";
import { OAuth2Tab, oAuth2TabsOpen } from "../../logic/Auth/UI/OAuth2Tab";
import { appGlobal } from "../../logic/app";
import mailIcon from "lucide-svelte/icons/mail";
import EditIcon from "lucide-svelte/icons/pencil";
import AuthIcon from "lucide-svelte/icons/key-round";
import { derived } from "svelte/store";
import { gt } from "../../l10n/l10n";
import { openFloatingCompose, shouldOpenComposeInWindow } from "./Composer/composeFloating";
import { openNativeComposeWindow, supportsNativeComposeWindow } from "./Composer/composeNative";
import { get } from "svelte/store";
import { selectedApp } from "../AppsBar/selectedApp";

export class MailJackdawApp extends JackdawApp {
  id = "mail";
  name = gt`Mail`;
  icon = mailIcon;
  appURL = "/mail";

  writeMail(mail: EMail) {
    let composerApp = new WriteMailJackdawApp();
    composerApp.title = derived(mail, () => mail.subject ?? composerApp.name);
    composerApp.windowParams = { mail: mail };
    mailApp.subApps.add(composerApp);
    if (shouldOpenComposeInWindow()) {
      if (supportsNativeComposeWindow()) {
        void openNativeComposeWindow(composerApp, mail)
          .catch(() => openFloatingCompose(composerApp, mail));
      } else {
        openFloatingCompose(composerApp, mail);
      }
      if (get(selectedApp)?.id === "mail-write") {
        openApp(mailApp, {});
      }
      return;
    }
    openApp(composerApp, composerApp.windowParams);
  }

  login(tab: OAuth2Tab): LoginDialogJackdawApp {
    let loginApp = new LoginDialogJackdawApp();
    let account = tab.oAuth2.account;
    loginApp.title = derived(account, () => gt`Login to ${account.name}`);
    loginApp.windowParams = { dialog: tab };
    mailApp.subApps.add(loginApp);
    openApp(loginApp, loginApp.windowParams);
    return loginApp;
  }
}

export const mailApp = new MailJackdawApp();

export function handleNativeComposeWindowClosed(windowID: string): void {
  let composer = mailApp.subApps.find(app =>
    app instanceof WriteMailJackdawApp && app.windowParams?.nativeComposeWindowID === windowID);
  if (composer) {
    mailApp.subApps.remove(composer);
  }
}

export class WriteMailJackdawApp extends JackdawApp {
  id = "mail-write";
  name = gt`Compose`;
  icon = EditIcon;
  appURL = "/mail/compose";
}

export class LoginDialogJackdawApp extends JackdawApp {
  id = "auth-login";
  name = gt`Login`;
  icon = AuthIcon;
  appURL = "/login";
}

const tabsObserver = {
  added(tabs: OAuth2Tab[]) {
    for (let tab of tabs) {
      // TODO simplify, using URLs? But remove correctly.
      tab.launcherApp = mailApp.login(tab);
    }
  },
  removed(tabs: OAuth2Tab[]) {
    for (let tab of tabs) {
      mailApp.subApps.remove(tab.launcherApp);
    }
    openApp(mailApp, {});
  },
};
oAuth2TabsOpen.registerObserver(tabsObserver);
(appGlobal as any)._oAuth2TabsObserver = tabsObserver; // HACK to keep it alive
