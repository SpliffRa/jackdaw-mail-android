import { getLocalStorage } from "../../Util/LocalStorage";
import type { MailAccount } from "../../../logic/Mail/MailAccount";

export type MessageViewerBackground = "white" | "theme" | "dark";
export type MessageContentRendering = "html" | "with-external" | "plaintext" | "source";

const kMessageViewerBackgroundSetting = "mail.read.background";
const kMessageContentRenderingSetting = "mail.contentRendering";

/** Общая настройка фона панели чтения и содержимого письма. */
export function getMessageViewerBackgroundSetting() {
  return getLocalStorage<MessageViewerBackground>(kMessageViewerBackgroundSetting, "theme");
}

/** Безопасное значение для старых или вручную изменённых настроек. */
export function normalizeMessageViewerBackground(value: unknown): MessageViewerBackground {
  if (value === "white" || value === "dark") {
    return value;
  }
  return "theme";
}

/**
 * Возвращает режим отображения для конкретного почтового ящика.
 * Старую общую настройку используем как начальное значение для совместимости.
 */
export function getMessageContentRenderingSetting(account?: MailAccount | null) {
  if (!account?.id) {
    return getLocalStorage<MessageContentRendering>(kMessageContentRenderingSetting, "html");
  }
  let legacyValue = getLocalStorage<MessageContentRendering>(kMessageContentRenderingSetting, "html").value;
  let defaultValue = normalizeMessageContentRendering(legacyValue);
  let setting = getLocalStorage<MessageContentRendering>(
    `${kMessageContentRenderingSetting}.account.${account.id}`,
    defaultValue,
  );
  // Keep an account without an explicit override in sync with the global
  // default even when this observable was created before that setting changed.
  setting.withDefault(defaultValue);
  return setting;
}

/** Безопасное значение для старых или вручную изменённых настроек. */
export function normalizeMessageContentRendering(value: unknown): MessageContentRendering {
  if (value === "with-external" || value === "plaintext" || value === "source") {
    return value;
  }
  return "html";
}
