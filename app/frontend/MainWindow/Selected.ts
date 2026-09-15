import type { Workspace } from "../../logic/Abstract/Workspace";
import { writable } from "svelte/store";
import { getLocalStorage } from "../Util/LocalStorage";

export const selectedWorkspace = writable<Workspace>();

const selectedWorkspaceID = getLocalStorage("workspace.selected", "");

/** Выбирает пространство и запоминает его для следующего запуска. */
export function selectWorkspace(workspace: Workspace | null | undefined): void {
  selectedWorkspace.set(workspace ?? undefined);
  selectedWorkspaceID.value = workspace?.id ?? "";
}

/** Восстанавливает последнее пространство после загрузки списка пространств. */
export function restoreSelectedWorkspace(workspaces: Iterable<Workspace>): void {
  const savedID = selectedWorkspaceID.value;
  const restoredWorkspace = savedID
    ? [...workspaces].find(workspace => workspace.id == savedID)
    : undefined;
  selectedWorkspace.set(restoredWorkspace);
  if (savedID && !restoredWorkspace) {
    selectedWorkspaceID.value = "";
  }
}

/** Hack. Modify this store when you change the `Account.workspace`
 * of any account. This signals the UI to refresh the account lists.
 * The value of the store does not matter. */
export const changedWorkspace = writable(1);
