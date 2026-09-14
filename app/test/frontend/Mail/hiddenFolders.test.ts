// @vitest-environment happy-dom
import { afterEach, beforeAll, describe, expect, test } from "vitest";
import { tick, mount, unmount } from "svelte";
import { ArrayColl } from "svelte-collections";

let HiddenFolders: any;
let hiddenFoldersEpoch: any;
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
  HiddenFolders = (
    await import("../../../frontend/Mail/LeftPane/HiddenFolders.svelte")
  ).default;
  hiddenFoldersEpoch = (
    await import("../../../frontend/Mail/LeftPane/hiddenFolders")
  ).hiddenFoldersEpoch;
});

afterEach(() => {
  for (let instance of mounted) {
    unmount(instance);
  }
  mounted = [];
  hiddenFoldersEpoch.set(0);
  localStorageValues.clear();
  document.body.replaceChildren();
});

function account(id: string, name: string, workspace: any): any {
  return {
    id,
    name,
    protocol: "imap",
    workspace,
    dependentAccounts: () => new ArrayColl(),
    getAllFolders: () => new ArrayColl(),
  };
}

function render(accounts: any[]): HTMLElement {
  let target = document.createElement("div");
  document.body.append(target);
  mounted.push(mount(HiddenFolders, {
    target,
    props: { accounts: new ArrayColl(accounts) },
  }));
  return target;
}

function setHiddenFolders(...accountIDs: string[]): void {
  localStorageValues.set("mail.folders.hidden", JSON.stringify(
    accountIDs.map((accountId, index) => ({
      accountId,
      folderId: `folder-${index}`,
      folderPath: `Folder ${index + 1}`,
    })),
  ));
}

describe("скрытые папки", () => {
  test("группирует папки по рабочему пространству и ящику", async () => {
    let work = { id: "work", name: "Work", color: "lightblue" };
    let personal = { id: "personal", name: "Personal", color: "lightgreen" };
    let workAccount = account("work-account", "Work Mail", work);
    let secondWorkAccount = account("second-work-account", "Second Work Mail", work);
    let personalAccount = account("personal-account", "Personal Mail", personal);
    setHiddenFolders(workAccount.id, secondWorkAccount.id, personalAccount.id);

    let target = render([workAccount, secondWorkAccount, personalAccount]);
    await tick();

    expect(target.querySelector(".count")?.textContent).toBe("3");
    expect([...target.querySelectorAll(".hidden-workspace-group")]
      .map(element => element.textContent?.trim()))
      .toEqual(["Work", "Personal"]);
    expect([...target.querySelectorAll(".hidden-account-group")]
      .map(element => element.textContent?.trim()))
      .toEqual(["Work Mail", "Second Work Mail", "Personal Mail"]);
  });

  test("показывает только папки аккаунтов выбранного пространства", async () => {
    let work = { id: "work", name: "Work", color: "lightblue" };
    let personal = { id: "personal", name: "Personal", color: "lightgreen" };
    let workAccount = account("work-account-filtered", "Work Mail", work);
    let personalAccount = account("personal-account-filtered", "Personal Mail", personal);
    setHiddenFolders(workAccount.id, personalAccount.id);

    let target = render([workAccount]);
    await tick();

    expect(target.querySelector(".count")?.textContent).toBe("1");
    expect(target.textContent).toContain("Work Mail");
    expect(target.textContent).not.toContain("Personal Mail");
    expect(target.textContent).not.toContain("Folder 2");
  });
});
