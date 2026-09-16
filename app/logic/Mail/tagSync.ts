// #if [PROPRIETARY]
import { appGlobal } from "../app";
import { ArrayColl } from "svelte-collections";
import {
  getTagsSyncAccountId,
  setTagsSyncAccountId,
  syncTagsFromMasterCategoryList,
  type SyncTagsResult,
} from "../Abstract/Tag";
import { OWAAccount } from "./OWA/OWAAccount";

/** OWA mailboxes whose Master Category List can be synced into Settings. */
export function listOWAAccountsForTagSync(): OWAAccount[] {
  let accounts = new ArrayColl<OWAAccount>();
  for (let account of appGlobal.emailAccounts) {
    if (account instanceof OWAAccount && account.isLoggedIn) {
      accounts.add(account);
    }
  }
  return accounts.contents.sort((a, b) => {
    if (a.isDependentAccount != b.isDependentAccount) {
      return a.isDependentAccount ? 1 : -1;
    }
    return a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
  });
}

export function getConfiguredTagSyncAccount(): OWAAccount | null {
  let syncAccountId = getTagsSyncAccountId();
  if (!syncAccountId) {
    return null;
  }
  let account = appGlobal.emailAccounts.find(entry => entry.id == syncAccountId);
  return account instanceof OWAAccount ? account : null;
}

export function resolveOWAAccountForTagSync(fallbackPrimary?: OWAAccount | null): OWAAccount | null {
  let configured = getConfiguredTagSyncAccount();
  if (configured?.isLoggedIn) {
    return configured;
  }
  let accounts = listOWAAccountsForTagSync();
  let shared = accounts.filter(account => account.isDependentAccount);
  if (shared.length) {
    return shared[0];
  }
  if (fallbackPrimary && !fallbackPrimary.isDependentAccount && fallbackPrimary.isLoggedIn) {
    return fallbackPrimary;
  }
  return accounts.find(account => !account.isDependentAccount) ?? null;
}

export async function syncTagsFromOWAAccount(
  account: OWAAccount,
  removeOthers = true,
): Promise<SyncTagsResult> {
  if (!account.isLoggedIn) {
    throw new Error("Mailbox is not logged in");
  }
  let entries = await account.fetchMasterCategoryList();
  setTagsSyncAccountId(account.id);
  return await syncTagsFromMasterCategoryList(entries, { removeOthers });
}
// #endif
