import { getLocalStorage } from "../../Util/LocalStorage";

export const kMessageZoomMin = 50;
export const kMessageZoomMax = 200;
export const kMessageZoomStep = 10;
export const kMessageZoomDefault = 100;

export function getMessageZoomSetting() {
  return getLocalStorage("mail.read.zoom", kMessageZoomDefault);
}

export function clampMessageZoom(value: number): number {
  return Math.min(kMessageZoomMax, Math.max(kMessageZoomMin, Math.round(value / kMessageZoomStep) * kMessageZoomStep));
}

export function stepMessageZoom(current: number, direction: 1 | -1): number {
  return clampMessageZoom(current + direction * kMessageZoomStep);
}

export function isMessageZoomWheelEvent(event: WheelEvent): boolean {
  return event.ctrlKey || event.metaKey;
}

export function isMessageZoomKeyEvent(event: KeyboardEvent): boolean {
  if (!event.ctrlKey && !event.metaKey) {
    return false;
  }
  if (event.altKey || event.shiftKey) {
    return false;
  }
  return event.key == "=" || event.key == "+" || event.key == "-" || event.key == "0";
}

export function messageZoomKeyDirection(event: KeyboardEvent): 1 | -1 | 0 | null {
  if (!isMessageZoomKeyEvent(event)) {
    return null;
  }
  if (event.key == "0") {
    return 0;
  }
  return event.key == "-" ? -1 : 1;
}

export function messageZoomFactor(zoom: number): number {
  return zoom / 100;
}

/** CSS inside the email iframe: reflow text when using non-layout zoom. */
export function messageZoomReflowCss(
  factor: number,
  mode: "css-zoom" | "text-wrap" = "css-zoom",
): string {
  if (factor == 1) {
    return "";
  }
  let wrapRules = `
body {
  overflow-x: auto !important;
  overflow-wrap: anywhere;
  word-break: break-word;
}
table {
  max-width: 100%;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
`;
  if (mode == "text-wrap") {
    return wrapRules;
  }
  return `
html {
  zoom: ${factor};
}
body {
  width: calc(100% / ${factor}) !important;
  max-width: calc(100% / ${factor}) !important;
  box-sizing: border-box;
  overflow-x: auto !important;
  overflow-wrap: anywhere;
  word-break: break-word;
}
table {
  max-width: 100%;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
`;
}

export function messageZoomHeadStyle(zoom: number): string {
  if (zoom == kMessageZoomDefault) {
    return "";
  }
  return `<style id="jackdaw-message-zoom">${messageZoomReflowCss(messageZoomFactor(zoom))}</style>`;
}

export function isMacPlatform(): boolean {
  return typeof navigator != "undefined" &&
    /Mac|iPhone|iPad/i.test(navigator.platform);
}
