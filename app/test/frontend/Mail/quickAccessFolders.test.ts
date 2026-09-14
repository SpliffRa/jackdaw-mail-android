// @vitest-environment happy-dom
import { afterEach, beforeAll, describe, expect, test } from "vitest";
import { tick, mount, unmount } from "svelte";
import { ArrayColl } from "svelte-collections";

let QuickAccessFolders: any;
let favoriteFoldersEpoch: any;
let watchMailFolderTrees: any;
let enumerateMailAccounts: any;
let moveFavoriteFolder: any;
let quickAccessEpoch: any;
let mounted: ReturnType<typeof mount>[] = [];
let localStorageValues = new Map<string, string>();

Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: {
    getItem: (key: string) => localStorageValues.get(key) ?? null,
    setItem: (key: string, value: string) => localStorageValues.set(key, value),
    removeItem: (key: string) => localStorageValues.delete(key),
  },
});

beforeAll(async () => {
  await import("../../../logic/app");
  QuickAccessFolders = (
    await import("../../../frontend/Mail/LeftPane/QuickAccessFolders.svelte")
  ).default;
  favoriteFoldersEpoch = (await import("../../../frontend/Mail/LeftPane/favoriteFolders")).favoriteFoldersEpoch;
  watchMailFolderTrees = (await import("../../../frontend/Mail/LeftPane/favoriteFolders")).watchMailFolderTrees;
  enumerateMailAccounts = (await import("../../../frontend/Mail/LeftPane/favoriteFolders")).enumerateMailAccounts;
  moveFavoriteFolder = (await import("../../../frontend/Mail/LeftPane/favoriteFolders")).moveFavoriteFolder;
  quickAccessEpoch = (await import("../../../frontend/Mail/LeftPane/quickAccessUtils")).quickAccessEpoch;
});

afterEach(() => {
  for (let instance of mounted) {
    unmount(instance);
  }
  mounted = [];
  favoriteFoldersEpoch.set(0);
  quickAccessEpoch.set(0);
  localStorageValues.clear();
  document.body.replaceChildren();
});

describe("QuickAccessFolders", () => {
  test("treats a default quick-access folder as visible in favorites", async () => {
    let folders = new ArrayColl<any>();
    let account: any = {
      id: "account-default",
      name: "Test account",
      protocol: "owa",
      dependentAccounts: () => new ArrayColl(),
      findSpecialFolder: () => null,
      getAllFolders: () => folders,
    };
    let folder: any = {
      id: "folder-trash",
      name: "Trash",
      fullPath: "Trash",
      account,
      countUnread: 0,
      countNewArrived: 0,
      subscribe(observer: (folder: any, property: string | null, oldValue: any) => void) {
        observer(this, null, null);
        return () => {};
      },
    };
    folders.add(folder);

    let target = document.createElement("div");
    document.body.append(target);
    mounted.push(mount(QuickAccessFolders, {
      target,
      props: { accounts: new ArrayColl([account]), account, selectedFolder: null },
    }));
    await tick();

    let quickFolder = target.querySelector("button.quick-folder");
    expect(quickFolder).not.toBeNull();
    quickFolder!.dispatchEvent(new MouseEvent("contextmenu", {
      bubbles: true,
      clientX: 20,
      clientY: 20,
    }));
    await tick();

    let labels = [...document.querySelectorAll("button.menuitem .label")]
      .map(element => element.textContent?.trim());
    expect(labels).toContain("Remove from favorites");
    expect(labels).not.toContain("Show in favorites");

    let removeButton = [...document.querySelectorAll("button.menuitem")]
      .find(button => button.textContent?.includes("Remove from favorites"));
    expect(removeButton).toBeDefined();
    removeButton!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await tick();

    expect(target.querySelector("button.quick-folder")).toBeNull();
    expect(localStorageValues.get("mail.folders.hidden")).toBeUndefined();
  });

  test("keeps a default folder removed when favorite and quick-access objects differ", async () => {
    let folders = new ArrayColl<any>();
    let account: any = {
      id: "account-default-clone",
      name: "Test account",
      protocol: "owa",
      dependentAccounts: () => new ArrayColl(),
      findSpecialFolder: () => defaultFolder,
      getAllFolders: () => folders,
    };
    let favoriteFolder: any = {
      id: "folder-trash-clone",
      name: "Trash",
      fullPath: "Trash",
      account,
      countUnread: 0,
      countNewArrived: 0,
      subscribe(observer: (folder: any, property: string | null, oldValue: any) => void) {
        observer(this, null, null);
        return () => {};
      },
    };
    let defaultFolder: any = { ...favoriteFolder };
    folders.add(favoriteFolder);
    localStorageValues.set("mail.folders.favorites", JSON.stringify([{
      accountId: account.id,
      folderId: favoriteFolder.id,
      folderPath: favoriteFolder.fullPath,
    }]));

    let target = document.createElement("div");
    document.body.append(target);
    mounted.push(mount(QuickAccessFolders, {
      target,
      props: { accounts: new ArrayColl([account]), account, selectedFolder: null },
    }));
    await tick();

    let quickFolder = target.querySelector("button.quick-folder");
    expect(quickFolder).not.toBeNull();
    quickFolder!.dispatchEvent(new MouseEvent("contextmenu", {
      bubbles: true,
      clientX: 20,
      clientY: 20,
    }));
    await tick();

    let removeButton = [...document.querySelectorAll("button.menuitem")]
      .find(button => button.textContent?.includes("Remove from favorites"));
    expect(removeButton).toBeDefined();
    removeButton!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await tick();

    expect(target.querySelector("button.quick-folder")).toBeNull();
  });

  test("refreshes a favorite after its folder appears", async () => {
    let folders = new ArrayColl<any>();
    let account: any = {
      id: "account-1",
      name: "Test account",
      protocol: "owa",
      dependentAccounts: () => new ArrayColl(),
      findSpecialFolder: () => null,
      getAllFolders: () => folders,
    };
    let accounts = new ArrayColl<any>();
    accounts.add(account);
    localStorageValues.set("mail.folders.favorites", JSON.stringify([{
      accountId: account.id,
      folderId: "folder-1",
      folderPath: "Inbox",
    }]));

    let target = document.createElement("div");
    document.body.append(target);
    mounted.push(mount(QuickAccessFolders, {
      target,
      props: { accounts, account, selectedFolder: null },
    }));

    expect(target.querySelector("button.quick-folder")).toBeNull();
    expect(target.querySelector(".quick-folder.pending")).toBeNull();

    let folder: any = {
      id: "folder-1",
      name: "Inbox",
      fullPath: "Inbox",
      account,
      countUnread: 0,
      countNewArrived: 0,
      subscribe(observer: (folder: any, property: string | null, oldValue: any) => void) {
        observer(this, null, null);
        return () => {};
      },
    };
    folders.add(folder);
    favoriteFoldersEpoch.update(value => value + 1);
    await tick();

    expect(target.querySelector("button.quick-folder")).not.toBeNull();
  });

  test("refreshes a favorite when the accounts collection appears", async () => {
    let folders = new ArrayColl<any>();
    let account: any = {
      id: "account-2",
      name: "Test account",
      protocol: "owa",
      dependentAccounts: () => new ArrayColl(),
      findSpecialFolder: () => null,
      getAllFolders: () => folders,
    };
    let accounts = new ArrayColl<any>();
    localStorageValues.set("mail.folders.favorites", JSON.stringify([{
      accountId: account.id,
      folderId: "folder-2",
      folderPath: "Inbox",
    }]));

    let folder: any = {
      id: "folder-2",
      name: "Inbox",
      fullPath: "Inbox",
      account,
      countUnread: 0,
      countNewArrived: 0,
      subscribe(observer: (folder: any, property: string | null, oldValue: any) => void) {
        observer(this, null, null);
        return () => {};
      },
    };
    folders.add(folder);
    let target = document.createElement("div");
    document.body.append(target);
    mounted.push(mount(QuickAccessFolders, {
      target,
      props: { accounts, account, selectedFolder: null },
    }));

    expect(target.querySelector("button.quick-folder")).toBeNull();

    accounts.add(account);
    await tick();

    expect(target.querySelector("button.quick-folder")).not.toBeNull();
  });

  test("enumerates mail accounts inside the all-accounts entry", () => {
    let account: any = {
      id: "account-3",
      protocol: "owa",
      dependentAccounts: () => new ArrayColl(),
    };
    let allAccounts: any = {
      id: "all-accounts",
      protocol: "all",
      accounts: new ArrayColl([account]),
      dependentAccounts: () => new ArrayColl(),
    };

    expect(enumerateMailAccounts(new ArrayColl([allAccounts]))).toEqual([account]);
  });

  test("does not stop tracking when an account has non-mail dependents", () => {
    let rootFolders = new ArrayColl<any>();
    let calendar = {
      id: "calendar-1",
      protocol: "calendar-owa",
      dependentAccounts: () => new ArrayColl(),
    };
    let account: any = {
      id: "account-1",
      protocol: "owa",
      rootFolders,
      dependentAccounts: () => new ArrayColl([calendar]),
      subscribe(observer: () => void) {
        observer();
        return () => {};
      },
    };
    let accounts = new ArrayColl<any>([account]);
    let changes = 0;
    let stop = watchMailFolderTrees(accounts, () => changes++);

    rootFolders.add({
      id: "folder-1",
      subFolders: new ArrayColl(),
    });

    expect(changes).toBeGreaterThan(1);
    stop();
  });

  test("moves a favorite past hidden entries in the visible order", () => {
    let account: any = {
      id: "account-5",
      protocol: "owa",
    };
    let favoriteRefs = [
      { accountId: account.id, folderId: "folder-a", folderPath: "A" },
      { accountId: account.id, folderId: "folder-b", folderPath: "B" },
      { accountId: account.id, folderId: "folder-c", folderPath: "C" },
    ];
    localStorageValues.set("mail.folders.favorites", JSON.stringify(favoriteRefs));

    let folder: any = {
      id: "folder-a",
      name: "A",
      fullPath: "A",
      account,
    };
    moveFavoriteFolder(folder, "down", [favoriteRefs[0], favoriteRefs[2]]);

    expect(JSON.parse(localStorageValues.get("mail.folders.favorites")!)).toEqual([
      favoriteRefs[2],
      favoriteRefs[1],
      favoriteRefs[0],
    ]);
  });

  test("notifies when a mail account is added after tracking starts", () => {
    let accounts = new ArrayColl<any>();
    let changes = 0;
    let stop = watchMailFolderTrees(accounts, () => changes++);
    let account: any = {
      id: "account-4",
      protocol: "owa",
      rootFolders: new ArrayColl(),
      dependentAccounts: () => new ArrayColl(),
      subscribe() {
        return () => {};
      },
    };

    accounts.add(account);

    expect(changes).toBeGreaterThan(1);
    stop();
  });
});
