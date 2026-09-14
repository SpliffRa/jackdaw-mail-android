import { describe, expect, test, vi } from "vitest";
import {
  getMessageContentRenderingSetting,
  normalizeMessageContentRendering,
  normalizeMessageViewerBackground,
} from "../../../frontend/Mail/Message/messageViewerAppearance";
import type { MailAccount } from "../../../logic/Mail/MailAccount";

describe("фон просмотровщика сообщений", () => {
  test("по умолчанию используется фон темы", () => {
    expect(normalizeMessageViewerBackground(undefined)).toBe("theme");
    expect(normalizeMessageViewerBackground("legacy-value")).toBe("theme");
  });

  test("сохраняет поддерживаемые варианты", () => {
    expect(normalizeMessageViewerBackground("white")).toBe("white");
    expect(normalizeMessageViewerBackground("theme")).toBe("theme");
    expect(normalizeMessageViewerBackground("dark")).toBe("dark");
  });
});

describe("режим отображения содержимого письма", () => {
  test("по умолчанию отключает внешнее содержимое", () => {
    expect(normalizeMessageContentRendering(undefined)).toBe("html");
    expect(normalizeMessageContentRendering("unknown")).toBe("html");
  });

  test("сохраняет поддерживаемые режимы", () => {
    expect(normalizeMessageContentRendering("html")).toBe("html");
    expect(normalizeMessageContentRendering("with-external")).toBe("with-external");
    expect(normalizeMessageContentRendering("plaintext")).toBe("plaintext");
    expect(normalizeMessageContentRendering("source")).toBe("source");
  });

  test("хранит режим отдельно для каждого ящика", () => {
    let values = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
    });

    let accountA = { id: "appearance-test-a" } as MailAccount;
    let accountB = { id: "appearance-test-b" } as MailAccount;
    let settingA = getMessageContentRenderingSetting(accountA);
    let settingB = getMessageContentRenderingSetting(accountB);

    expect(settingA.value).toBe("html");
    expect(settingB.value).toBe("html");

    settingA.value = "with-external";

    expect(getMessageContentRenderingSetting(accountA).value).toBe("with-external");
    expect(getMessageContentRenderingSetting(accountB).value).toBe("html");
    vi.unstubAllGlobals();
  });

  test("использует общую настройку, пока у ящика нет переопределения", () => {
    let values = new Map<string, string>([
      ["mail.contentRendering", JSON.stringify("plaintext")],
    ]);
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
    });

    let account = { id: "appearance-test-global" } as MailAccount;
    expect(getMessageContentRenderingSetting(account).value).toBe("plaintext");
    vi.unstubAllGlobals();
  });
});
