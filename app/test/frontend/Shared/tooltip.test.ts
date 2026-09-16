// @vitest-environment happy-dom

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { installTooltips } from "../../../frontend/Shared/tooltip";

let uninstallTooltips: () => void;

beforeEach(() => {
  vi.useFakeTimers();
  document.body.replaceChildren();
  uninstallTooltips = installTooltips(document);
});

afterEach(() => {
  uninstallTooltips?.();
  document.body.replaceChildren();
  vi.useRealTimers();
});

function hover(element: HTMLElement): void {
  element.dispatchEvent(new PointerEvent("pointerover", { bubbles: true }));
}

describe("renderer tooltips", () => {
  test("shows a stable body-level tooltip for a native title", () => {
    const button = document.createElement("button");
    button.title = "Настройки";
    button.textContent = "⚙";
    document.body.append(button);

    hover(button);
    vi.advanceTimersByTime(420);

    const tooltip = document.querySelector<HTMLElement>("#jackdaw-tooltip");
    expect(tooltip?.textContent).toBe("Настройки");
    expect(tooltip?.dataset.visible).toBe("true");
    expect(button.hasAttribute("title")).toBe(false);
    expect(button.getAttribute("aria-describedby")).toBe("jackdaw-tooltip");
  });

  test("does not restart while the pointer moves inside the same button", () => {
    const button = document.createElement("button");
    button.setAttribute("aria-label", "Обновить");
    const icon = document.createElement("span");
    button.append(icon);
    document.body.append(button);

    hover(button);
    icon.dispatchEvent(new PointerEvent("pointerover", { bubbles: true }));
    vi.advanceTimersByTime(419);
    expect(document.querySelector<HTMLElement>("#jackdaw-tooltip")?.dataset.visible).toBe("false");

    vi.advanceTimersByTime(1);
    expect(document.querySelector<HTMLElement>("#jackdaw-tooltip")?.dataset.visible).toBe("true");
  });

  test("restores the native title after leaving and hides the tooltip", () => {
    const button = document.createElement("button");
    button.title = "Закрыть";
    document.body.append(button);

    hover(button);
    vi.advanceTimersByTime(420);
    button.dispatchEvent(new PointerEvent("pointerout", { bubbles: true, relatedTarget: document.body }));
    vi.advanceTimersByTime(140);

    expect(button.getAttribute("title")).toBe("Закрыть");
    expect(button.hasAttribute("aria-describedby")).toBe(false);
    expect(document.querySelector<HTMLElement>("#jackdaw-tooltip")?.hidden).toBe(true);
  });
});
