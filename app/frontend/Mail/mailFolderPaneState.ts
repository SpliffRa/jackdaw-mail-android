import { getLocalStorage } from "../Util/LocalStorage";

/** Состояние видимости панели папок почты. */
export const mailFolderPaneExpandedSetting = getLocalStorage(
  "mail.folder-pane.expanded",
  true,
);

export function toggleMailFolderPane(): void {
  mailFolderPaneExpandedSetting.value = !mailFolderPaneExpandedSetting.value;
}
