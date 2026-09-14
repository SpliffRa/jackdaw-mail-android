import { afterEach, expect, test, vi } from "vitest";
import {
  getMailAccountNotificationSetting,
  getMailNotificationSound,
  readMailAccountNotificationSettings,
  updateMailAccountNotificationSettings,
} from "../../../frontend/Mail/mailNotificationSettings";
import type { MailAccount } from "../../../logic/Mail/MailAccount";

afterEach(() => {
  vi.unstubAllGlobals();
});

function stubStorage(): Map<string, string> {
  let values = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  });
  return values;
}

test("хранит настройки уведомлений отдельно для разных ящиков", () => {
  stubStorage();
  let accountA = { id: "notification-test-a" } as MailAccount;
  let accountB = { id: "notification-test-b" } as MailAccount;

  updateMailAccountNotificationSettings(accountA, { enabled: false, sound: "bell" });

  expect(readMailAccountNotificationSettings(getMailAccountNotificationSetting(accountA).value)).toMatchObject({
    enabled: false,
    sound: "bell",
  });
  expect(readMailAccountNotificationSettings(getMailAccountNotificationSetting(accountB).value)).toMatchObject({
    enabled: true,
    sound: "global",
  });
});

test("возвращает пользовательский звук для выбранного ящика", () => {
  stubStorage();
  let account = { id: "notification-test-custom" } as MailAccount;
  let dataURL = "data:audio/wav;base64,AAAA";

  updateMailAccountNotificationSettings(account, {
    sound: "custom",
    customSoundDataURL: dataURL,
    customSoundName: "office.wav",
  });

  expect(getMailNotificationSound(account)).toEqual({
    id: "custom",
    dataURL,
    name: "office.wav",
  });
});

test("возвращает глобальный звук для повреждённой настройки пользовательского звука", () => {
  stubStorage();
  let account = { id: "notification-test-invalid-custom" } as MailAccount;

  updateMailAccountNotificationSettings(account, { sound: "custom" });

  expect(readMailAccountNotificationSettings(getMailAccountNotificationSetting(account).value).sound)
    .toBe("global");
});
