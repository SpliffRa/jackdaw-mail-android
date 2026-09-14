import type { Folder } from "../../logic/Mail/Folder";
import { writable } from "svelte/store";

export type MailTransferAction = "move" | "copy";
export type MailTransferState = "active" | "completed" | "error";

export interface MailTransferProgress {
  id: number;
  sourceFolder: Folder;
  action: MailTransferAction;
  state: MailTransferState;
  total: number;
  completed: number;
  targetName: string | null;
}

export const mailTransferProgress = writable<MailTransferProgress | null>(null);

let nextTransferID = 0;
let clearTimer: ReturnType<typeof setTimeout> | null = null;

function clearScheduledTimer(): void {
  if (clearTimer) {
    clearTimeout(clearTimer);
    clearTimer = null;
  }
}

function clearWhenCurrent(id: number, delay: number): void {
  clearScheduledTimer();
  clearTimer = setTimeout(() => {
    clearTimer = null;
    mailTransferProgress.update(current => current?.id == id ? null : current);
  }, delay);
}

export function startMailTransfer(
  sourceFolder: Folder,
  action: MailTransferAction,
  total: number,
  targetName: string | null = null,
): number {
  clearScheduledTimer();
  let id = ++nextTransferID;
  mailTransferProgress.set({
    id,
    sourceFolder,
    action,
    state: "active",
    total: Math.max(1, total),
    completed: 0,
    targetName,
  });
  return id;
}

export function updateMailTransfer(id: number, completed: number): void {
  mailTransferProgress.update(current => {
    if (!current || current.id != id) {
      return current;
    }
    return {
      ...current,
      completed: Math.min(current.total, Math.max(0, completed)),
    };
  });
}

export function completeMailTransfer(id: number): void {
  mailTransferProgress.update(current => current?.id == id
    ? { ...current, state: "completed", completed: current.total }
    : current);
  clearWhenCurrent(id, 1800);
}

export function failMailTransfer(id: number): void {
  mailTransferProgress.update(current => current?.id == id
    ? { ...current, state: "error" }
    : current);
  clearWhenCurrent(id, 4000);
}

export async function withMailTransferProgress<T>(
  sourceFolder: Folder,
  action: MailTransferAction,
  total: number,
  targetName: string | null,
  task: (update: (completed: number) => void) => Promise<T>,
): Promise<T> {
  let id = startMailTransfer(sourceFolder, action, total, targetName);
  try {
    let result = await task(completed => updateMailTransfer(id, completed));
    completeMailTransfer(id);
    return result;
  } catch (ex) {
    failMailTransfer(id);
    throw ex;
  }
}
