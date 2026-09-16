import type { EMail } from "../../../logic/Mail/EMail";
import type { PageParams } from "../../AppsBar/selectedApp";
import type { ComposeWindowMail } from "../../../logic/Mail/Composer/ComposeWindowProtocol";
import { serializeComposeMail } from "./composeWindow";

const kNativeComposeWindowID = "nativeComposeWindowID";

interface NativeComposeHost {
  windowParams: PageParams;
}

type NativeComposeAPI = {
  openComposeWindow(windowID: string, payload: ComposeWindowMail): void;
  focusComposeWindow(windowID: string): void;
};

const openingWindows = new Map<string, Promise<void>>();

function getNativeComposeAPI(): NativeComposeAPI | null {
  if (typeof window === "undefined") {
    return null;
  }
  const api = (window as any).api;
  if (typeof api?.openComposeWindow !== "function" || typeof api?.focusComposeWindow !== "function") {
    return null;
  }
  return api as NativeComposeAPI;
}

export function supportsNativeComposeWindow(): boolean {
  return !!getNativeComposeAPI();
}

export function getNativeComposeWindowID(host: NativeComposeHost): string | null {
  const windowID = host.windowParams?.[kNativeComposeWindowID];
  return typeof windowID === "string" && windowID ? windowID : null;
}

export function clearNativeComposeWindowID(host: NativeComposeHost, windowID?: string): void {
  if (!windowID || getNativeComposeWindowID(host) === windowID) {
    delete host.windowParams[kNativeComposeWindowID];
  }
}

/** Открывает или обновляет системное окно создания письма. */
export async function openNativeComposeWindow(host: NativeComposeHost, mail: EMail): Promise<void> {
  const api = getNativeComposeAPI();
  if (!api) {
    throw new Error("Native compose windows are unavailable");
  }
  let windowID = getNativeComposeWindowID(host);
  if (!windowID) {
    windowID = crypto.randomUUID();
    host.windowParams[kNativeComposeWindowID] = windowID;
  }
  const pending = openingWindows.get(windowID);
  if (pending) {
    return pending;
  }
  const currentWindowID = windowID;
  const operation = (async () => {
    const payload = await serializeComposeMail(mail, currentWindowID);
    api.openComposeWindow(currentWindowID, payload);
  })();
  openingWindows.set(currentWindowID, operation);
  try {
    await operation;
  } catch (ex) {
    clearNativeComposeWindowID(host, currentWindowID);
    throw ex;
  } finally {
    if (openingWindows.get(currentWindowID) === operation) {
      openingWindows.delete(currentWindowID);
    }
  }
}

export async function focusNativeComposeWindow(host: NativeComposeHost, mail: EMail): Promise<void> {
  const api = getNativeComposeAPI();
  if (!api) {
    throw new Error("Native compose windows are unavailable");
  }
  let windowID = getNativeComposeWindowID(host);
  if (!windowID) {
    await openNativeComposeWindow(host, mail);
    return;
  }
  api.focusComposeWindow(windowID);
}
