// @vitest-environment happy-dom

import { afterEach, beforeAll, describe, expect, test } from "vitest";
import { mount, tick, unmount } from "svelte";
import { ArrayColl } from "svelte-collections";
import "../../../logic/app";
import { MailIdentity } from "../../../logic/Mail/MailIdentity";

let IdentitySelector: any;
let mounted: ReturnType<typeof mount>[] = [];

beforeAll(async () => {
  IdentitySelector = (await import("../../../frontend/Mail/Composer/IdentitySelector.svelte")).default;
});

afterEach(() => {
  for (let instance of mounted) {
    unmount(instance);
  }
  mounted = [];
  document.body.replaceChildren();
});

function identity(account: any, emailAddress: string): MailIdentity {
  let result = new MailIdentity(account);
  result.emailAddress = emailAddress;
  result.realname = emailAddress;
  return result;
}

function render(selectedIdentity: MailIdentity, identities: MailIdentity[], workspace?: any): HTMLElement {
  let target = document.createElement("div");
  document.body.append(target);
  mounted.push(mount(IdentitySelector, {
    target,
    props: {
      selectedIdentity,
      identities: new ArrayColl(identities),
      fromAddress: selectedIdentity.emailAddress,
      fromName: selectedIdentity.realname,
      workspace,
    },
  }));
  return target;
}

describe("селектор отправителя", () => {
  test("показывает только адреса аккаунтов текущего пространства", async () => {
    let work = { id: "work" };
    let privateSpace = { id: "private" };
    let workAccount = { name: "Work", workspace: work };
    let secondWorkAccount = { name: "Second work", workspace: work };
    let privateAccount = { name: "Private", workspace: privateSpace };
    let workIdentity = identity(workAccount, "work@example.test");
    let secondWorkIdentity = identity(secondWorkAccount, "second@example.test");
    let privateIdentity = identity(privateAccount, "private@example.test");

    let target = render(workIdentity, [workIdentity, secondWorkIdentity, privateIdentity], work);
    await tick();

    let options = [...target.querySelectorAll("option")];
    expect(options).toHaveLength(2);
    expect(target.textContent).toContain("work@example.test");
    expect(target.textContent).toContain("second@example.test");
    expect(target.textContent).not.toContain("private@example.test");
  });

  test("без выбранного пространства сохраняет общий список адресов", async () => {
    let workAccount = { name: "Work", workspace: { id: "work" } };
    let privateAccount = { name: "Private", workspace: { id: "private" } };
    let workIdentity = identity(workAccount, "work@example.test");
    let privateIdentity = identity(privateAccount, "private@example.test");

    let target = render(workIdentity, [workIdentity, privateIdentity]);
    await tick();

    expect(target.querySelectorAll("option")).toHaveLength(2);
  });
});
