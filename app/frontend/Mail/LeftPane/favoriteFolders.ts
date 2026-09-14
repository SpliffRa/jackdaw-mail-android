import { getLocalStorage } from "../../Util/LocalStorage";
import type { MailAccount } from "../../../logic/Mail/MailAccount";
import type { Folder } from "../../../logic/Mail/Folder";
import type { Account } from "../../../logic/Abstract/Account";
import type { Collection } from "svelte-collections";
import { CollectionObserver, type ArrayColl } from "svelte-collections";
import { writable } from "svelte/store";
import { appGlobal } from "../../../logic/app";
import { folderQuickAccessKey, restoreQuickAccessFolder } from "./quickAccessUtils";

export interface FavoriteFolderRef {
  accountId: string;
  folderId: string;
  folderPath: string;
}

export const favoriteFoldersSetting = getLocalStorage<FavoriteFolderRef[]>("mail.folders.favorites", []);

/** Bumped when folder hierarchies load or change — refreshes favorite resolution. */
export const favoriteFoldersEpoch = writable(0);

let favoriteFolderTrackingStarted = false;

/** Mail accounts shown in the sidebar, including shared OWA mailboxes on the main account. */
export function enumerateMailAccounts(accounts: Collection<MailAccount>): MailAccount[] {
  let seen = new Set<string>();
  let result: MailAccount[] = [];
  function add(account: MailAccount | null | undefined) {
    if (!account?.id || account.protocol == "all" || seen.has(account.id)) {
      return;
    }
    seen.add(account.id);
    result.push(account);
  }
  for (let account of accounts) {
    if (account.protocol == "all") {
      let nestedAccounts = (account as MailAccount & { accounts?: Collection<MailAccount> }).accounts;
      let sourceAccounts = nestedAccounts?.hasItems ? nestedAccounts : appGlobal.emailAccounts;
      for (let nestedAccount of sourceAccounts) {
        add(nestedAccount);
      }
      continue;
    }
    add(account);
    for (let dependent of account.dependentAccounts()) {
      if (dependent.protocol == "owa" || dependent.protocol == "ews") {
        add(dependent as MailAccount);
      }
    }
  }
  return result;
}

export function getFavoriteFolderRefs(): FavoriteFolderRef[] {
  return favoriteFoldersSetting.value ?? [];
}

function normalizeFolderPath(path: string | null | undefined): string {
  return (path ?? "").replace(/^\/+|\/+$/g, "").replace(/\/+/g, "/");
}

function folderMatchesRef(folder: Folder, ref: FavoriteFolderRef): boolean {
  if (ref.folderId && folder.id == ref.folderId) {
    return true;
  }
  let refPath = normalizeFolderPath(ref.folderPath);
  if (!refPath) {
    return false;
  }
  let fullPath = normalizeFolderPath(folder.fullPath);
  if (refPath == fullPath || refPath == folder.name) {
    return true;
  }
  let accountName = folder.account?.name?.trim();
  if (accountName) {
    let qualified = normalizeFolderPath(`${accountName}/${fullPath}`);
    if (refPath == qualified) {
      return true;
    }
    if (refPath.endsWith("/" + fullPath) && refPath.startsWith(accountName + "/")) {
      return true;
    }
  }
  return false;
}

function sameFavoriteRef(a: FavoriteFolderRef, b: FavoriteFolderRef): boolean {
  if (a.accountId != b.accountId) {
    return false;
  }
  if (a.folderId && b.folderId && a.folderId == b.folderId) {
    return true;
  }
  const aPath = normalizeFolderPath(a.folderPath);
  const bPath = normalizeFolderPath(b.folderPath);
  return !!aPath && aPath == bPath;
}

export function isFavoriteFolderRef(folder: Folder, refs: FavoriteFolderRef[]): boolean {
  if (!folder.id || !folder.account?.id) {
    return false;
  }
  return refs.some(ref =>
    ref.accountId == folder.account.id && folderMatchesRef(folder, ref));
}

export function isFavoriteFolder(folder: Folder): boolean {
  return isFavoriteFolderRef(folder, getFavoriteFolderRefs());
}

export function addFavoriteFolder(folder: Folder): void {
  if (!folder.id || !folder.account?.id || folder.account.protocol == "all") {
    return;
  }
  restoreQuickAccessFolder(folder);
  if (isFavoriteFolder(folder)) {
    return;
  }
  favoriteFoldersSetting.value = [
    ...getFavoriteFolderRefs(),
    {
      accountId: folder.account.id,
      folderId: folder.id,
      folderPath: folder.fullPath,
    },
  ];
}

export function removeFavoriteFolder(folder: Folder): void {
  if (!folder.account?.id) {
    return;
  }
  favoriteFoldersSetting.value = getFavoriteFolderRefs().filter(ref =>
    !(ref.accountId == folder.account.id && folderMatchesRef(folder, ref)));
}

/** Move a favorite without changing the folder hierarchy on the server. */
export function moveFavoriteFolder(
  folder: Folder,
  direction: "up" | "down",
  visibleRefs?: FavoriteFolderRef[],
): void {
  if (!folder.account?.id) {
    return;
  }
  const refs = getFavoriteFolderRefs();
  const currentRef = refs.find(ref =>
    ref.accountId == folder.account.id && folderMatchesRef(folder, ref));
  if (!currentRef) {
    return;
  }
  const orderedVisibleRefs = (visibleRefs ?? refs).filter(ref =>
    refs.some(candidate => sameFavoriteRef(candidate, ref)));
  const visibleIndex = orderedVisibleRefs.findIndex(ref =>
    sameFavoriteRef(ref, currentRef));
  const targetVisibleIndex = visibleIndex + (direction == "up" ? -1 : 1);
  if (
    visibleIndex < 0 ||
    targetVisibleIndex < 0 ||
    targetVisibleIndex >= orderedVisibleRefs.length
  ) {
    return;
  }
  const targetRef = orderedVisibleRefs[targetVisibleIndex];
  const index = refs.findIndex(ref => sameFavoriteRef(ref, currentRef));
  const targetIndex = refs.findIndex(ref => sameFavoriteRef(ref, targetRef));
  if (index < 0 || targetIndex < 0 || index == targetIndex) {
    return;
  }
  const next = refs.slice();
  [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
  favoriteFoldersSetting.value = next;
}

export function toggleFavoriteFolder(folder: Folder): void {
  if (isFavoriteFolder(folder)) {
    removeFavoriteFolder(folder);
  } else {
    addFavoriteFolder(folder);
  }
}

export function resolveFavoriteFolders(
  accounts: Collection<MailAccount>,
  refs: FavoriteFolderRef[] = getFavoriteFolderRefs(),
): Folder[] {
  let resolved: Folder[] = [];
  let seen = new Set<string>();
  for (let ref of refs) {
    let folder = findFavoriteFolder(accounts, ref);
    if (!folder) {
      continue;
    }
    let key = folderQuickAccessKey(folder);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    resolved.push(folder);
  }
  return resolved;
}

export function findFavoriteFolder(
  accounts: Collection<MailAccount>,
  ref: FavoriteFolderRef,
): Folder | null {
  let account = enumerateMailAccounts(accounts).find(a => a.id == ref.accountId);
  if (!account) {
    return null;
  }
  return account.getAllFolders().find(f => folderMatchesRef(f, ref)) ?? null;
}

export function favoriteRefLabel(ref: FavoriteFolderRef, accounts?: Collection<MailAccount>): string {
  let accountName = accounts
    ? enumerateMailAccounts(accounts).find(a => a.id == ref.accountId)?.name
    : null;
  let path = normalizeFolderPath(ref.folderPath);
  let label = path
    ? (path.split("/").filter(Boolean).pop() ?? path)
    : ref.folderId;
  if (accountName && path && !path.startsWith(accountName + "/") && !path.startsWith(accountName)) {
    return `${accountName} / ${label}`;
  }
  return label;
}

/** Keep favorites in sync while folder hierarchies load after startup. */
export function watchMailFolderTrees(
  accounts: Collection<MailAccount>,
  onChange: () => void,
): () => void {
  let watchedAccounts = new Set<string>();
  let watchedSubtrees = new WeakSet<Folder>();
  let accountUnsubs: Array<() => void> = [];

  const foldersObserver = new (class extends CollectionObserver<Folder> {
    added(folders: Folder[] | ArrayColl<Folder>) {
      for (let folder of Array.from(folders)) {
        watchFolderSubtree(folder);
      }
      onChange();
    }
    removed() {
      onChange();
    }
  })();

  const dependentsObserver = new (class extends CollectionObserver<Account> {
    added(deps: Account[] | ArrayColl<Account>) {
      for (let dep of Array.from(deps)) {
        if (dep.protocol == "owa" || dep.protocol == "ews") {
          watchAccount(dep as MailAccount);
        }
      }
      onChange();
    }
    removed() {
      onChange();
    }
  })();

  const accountsObserver = new (class extends CollectionObserver<MailAccount> {
    added(accs: MailAccount[] | ArrayColl<MailAccount>) {
      for (let account of Array.from(accs)) {
        watchAccount(account);
      }
    }
    removed(accs: MailAccount[] | ArrayColl<MailAccount>) {
      for (let account of Array.from(accs)) {
        unwatchAccount(account);
      }
    }
  })();

  function watchFolderSubtree(folder: Folder) {
    if (watchedSubtrees.has(folder)) {
      return;
    }
    watchedSubtrees.add(folder);
    folder.subFolders.registerObserver(foldersObserver);
    for (let child of folder.subFolders.contents) {
      watchFolderSubtree(child);
    }
  }

  function watchAccount(account: MailAccount | null | undefined) {
    if (!account?.id || account.protocol == "all" || watchedAccounts.has(account.id)) {
      return;
    }
    watchedAccounts.add(account.id);
    account.rootFolders.registerObserver(foldersObserver);
    for (let folder of account.rootFolders.contents) {
      watchFolderSubtree(folder);
    }
    accountUnsubs.push(account.subscribe(() => onChange()));
    account.dependentAccounts().registerObserver(dependentsObserver);
    for (let dependent of account.dependentAccounts()) {
      if (dependent.protocol == "owa" || dependent.protocol == "ews") {
        watchAccount(dependent as MailAccount);
      }
    }
  }

  function unwatchAccount(account: MailAccount) {
    if (!account?.id) {
      return;
    }
    watchedAccounts.delete(account.id);
    account.rootFolders.unregisterObserver(foldersObserver);
    account.dependentAccounts().unregisterObserver(dependentsObserver);
  }

  accounts.registerObserver(accountsObserver);
  let accountsUnsub = accounts.subscribe(() => {
    for (let account of accounts) {
      watchAccount(account);
    }
    onChange();
  });

  return () => {
    accountsUnsub();
    accounts.unregisterObserver(accountsObserver);
    for (let unsub of accountUnsubs) {
      unsub();
    }
    accountUnsubs = [];
    for (let account of accounts) {
      unwatchAccount(account);
    }
    watchedAccounts.clear();
  };
}

/** Subscribe to folder trees on all mail accounts (call once at app startup). */
export function startFavoriteFolderTracking(): void {
  if (favoriteFolderTrackingStarted) {
    return;
  }
  favoriteFolderTrackingStarted = true;
  watchMailFolderTrees(appGlobal.emailAccounts, () => {
    favoriteFoldersEpoch.update(n => n + 1);
  });
}
