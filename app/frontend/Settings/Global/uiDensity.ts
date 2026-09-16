import { getLocalStorage } from "../../Util/LocalStorage";

export type UIDensity = "compact" | "normal" | "large";

export const uiDensitySetting = getLocalStorage<unknown>("appearance.density", "normal");

export function normalizeUIDensity(value: unknown): UIDensity {
  if (value == "compact" || value == "normal" || value == "large") {
    return value as UIDensity;
  }
  return "normal";
}
