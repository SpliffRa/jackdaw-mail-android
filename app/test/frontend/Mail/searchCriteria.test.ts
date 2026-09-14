// @vitest-environment happy-dom
import { afterEach, beforeAll, describe, expect, test } from "vitest";
import { mount, tick, unmount } from "svelte";

let SearchCriteria: any;
let SearchEMail: any;
let mounted: ReturnType<typeof mount>[] = [];

beforeAll(async () => {
  await import("../../../logic/app");
  SearchCriteria = (await import("../../../frontend/Mail/Search/SearchCriteria.svelte")).default;
  SearchEMail = (await import("../../../logic/Mail/Store/SearchEMail")).SearchEMail;
});

afterEach(() => {
  for (let instance of mounted) {
    unmount(instance);
  }
  mounted = [];
  document.body.replaceChildren();
});

describe("SearchCriteria", () => {
  test("сообщает об изменении после возврата tri-state фильтра в пустое состояние", async () => {
    let search = new SearchEMail();
    let changes = 0;
    let target = document.createElement("div");
    document.body.append(target);

    let instance = mount(SearchCriteria, {
      target,
      props: {
        search,
        showSearchTerm: false,
        showAccount: false,
      },
      events: { change: () => changes++ },
    });
    mounted.push(instance);
    await tick();

    let sentByMe = target.querySelector(".checkbox") as HTMLElement;
    sentByMe.click();
    await tick();
    expect(search.isOutgoing).toBe(true);

    sentByMe.click();
    await tick();
    expect(search.isOutgoing).toBe(false);

    sentByMe.click();
    await tick();
    expect(search.isOutgoing).toBeUndefined();
    expect(changes).toBe(3);
  });
});
