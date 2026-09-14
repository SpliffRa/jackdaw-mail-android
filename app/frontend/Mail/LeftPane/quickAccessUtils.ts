import type { MailAccount } from "../../../logic/Mail/MailAccount";
import type { Folder } from "../../../logic/Mail/Folder";
import { SpecialFolder } from "../../../logic/Mail/Folder";
import { writable } from "svelte/store";
import { getLocalStorage } from "../../Util/LocalStorage";

export interface QuickAccessFolderRef {
  accountId: string;
  folderId: string;
  folderPath: string;
}

/** User exclusions for system folders that are shown in quick access by default. */
export const quickAccessExcludedSetting = getLocalStorage<QuickAccessFolderRef[]>(
  "mail.folders.quickAccessExcluded",
  [],
);

/** Bumped when quick-access visibility preferences change. */
export const quickAccessEpoch = writable(0);

export const preferredSpecialFolders = [
  SpecialFolder.Inbox,
  SpecialFolder.Sent,
  SpecialFolder.Drafts,
  SpecialFolder.All,
  SpecialFolder.Spam,
  SpecialFolder.Trash,
  SpecialFolder.Archive,
];

/**
 * Saved OWA hierarchies can contain the right folders without retaining the
 * special-use marker. Prefer the marker, then use the stable system-folder
 * names as a fallback so quick access never becomes an empty heading.
 */
const fallbackNames: Partial<Record<SpecialFolder, string[]>> = {
  [SpecialFolder.Inbox]: ["inbox", "входящие"],
  [SpecialFolder.Sent]: ["sent", "sent items", "отправленные"],
  [SpecialFolder.Drafts]: ["drafts", "черновики"],
  [SpecialFolder.All]: ["all mail", "all messages", "все сообщения"],
  [SpecialFolder.Spam]: ["spam", "junk", "нежелательная почта", "спам"],
  [SpecialFolder.Trash]: ["trash", "deleted items", "удаленные", "корзина"],
  [SpecialFolder.Archive]: ["archive", "архив"],
};

function normalizeFolderName(name: string): string {
  return name.trim().toLocaleLowerCase().replace(/[._-]+/g, " ").replace(/\s+/g, " ");
}

function normalizeFolderPath(path: string | null | undefined): string {
  return (path ?? "").replace(/^\/+|\/+$/g, "").replace(/\/+/g, "/");
}

function folderMatchesRef(folder: Folder, ref: QuickAccessFolderRef): boolean {
  if (ref.folderId && folder.id == ref.folderId) {
    return true;
  }
  let refPath = normalizeFolderPath(ref.folderPath);
  return !!refPath && refPath == normalizeFolderPath(folder.fullPath);
}

function getQuickAccessExcludedRefs(): QuickAccessFolderRef[] {
  return quickAccessExcludedSetting.value ?? [];
}

export function isQuickAccessExcluded(
  folder: Folder,
  refs: QuickAccessFolderRef[] = getQuickAccessExcludedRefs(),
): boolean {
  if (!folder.id || !folder.account?.id || folder.account.protocol == "all") {
    return false;
  }
  return refs.some(ref =>
    ref.accountId == folder.account.id && folderMatchesRef(folder, ref));
}

export function excludeQuickAccessFolder(folder: Folder): void {
  if (!folder.id || !folder.account?.id ||
      folder.account.protocol == "all" || isQuickAccessExcluded(folder)) {
    return;
  }
  quickAccessExcludedSetting.value = [
    ...getQuickAccessExcludedRefs(),
    {
      accountId: folder.account.id,
      folderId: folder.id,
      folderPath: folder.fullPath,
    },
  ];
  quickAccessEpoch.update(value => value + 1);
}

export function restoreQuickAccessFolder(folder: Folder): void {
  let refs = getQuickAccessExcludedRefs();
  let visibleRefs = refs.filter(ref =>
    !(ref.accountId == folder.account?.id && folderMatchesRef(folder, ref)));
  if (visibleRefs.length == refs.length) {
    return;
  }
  quickAccessExcludedSetting.value = visibleRefs;
  quickAccessEpoch.update(value => value + 1);
}

export function findQuickAccessFolder(account: MailAccount, specialFolder: SpecialFolder): Folder | null {
  let marked = account.findSpecialFolder(specialFolder);
  if (marked) {
    return marked;
  }
  let names = fallbackNames[specialFolder] ?? [];
  return account.getAllFolders().find(folder => names.includes(normalizeFolderName(folder.name))) ?? null;
}

export function getDefaultQuickAccessFolders(account: MailAccount | null | undefined): Folder[] {
  if (!account) {
    return [];
  }
  return preferredSpecialFolders
    .map(specialFolder => findQuickAccessFolder(account, specialFolder))
    .filter((folder): folder is Folder => !!folder)
    .filter((folder, index, folders) => folders.indexOf(folder) == index);
}

export function folderQuickAccessKey(folder: Folder): string {
  return `${folder.account.id}:${folder.id ?? folder.fullPath}`;
}
