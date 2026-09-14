import { describe, expect, test } from "vitest";
import { currentMailSearchAccount } from "../../../frontend/Mail/Search/searchScope";
import type { Folder } from "../../../logic/Mail/Folder";
import type { MailAccount } from "../../../logic/Mail/MailAccount";

function account(id: string, protocol = "imap"): MailAccount {
  return { id, protocol } as MailAccount;
}

function folder(account: MailAccount): Folder {
  return { account } as Folder;
}

describe("область поиска почты", () => {
  test("берёт ящик из текущей папки", () => {
    let selectedAccount = account("old-account");
    let currentAccount = account("current-account");

    expect(currentMailSearchAccount(selectedAccount, folder(currentAccount)))
      .toBe(currentAccount);
  });

  test("использует выбранный ящик без текущей папки", () => {
    let selectedAccount = account("selected-account");

    expect(currentMailSearchAccount(selectedAccount, null)).toBe(selectedAccount);
  });

  test("не ограничивает поиск в объединённом представлении", () => {
    let allAccounts = account("all-accounts", "all");

    expect(currentMailSearchAccount(allAccounts, folder(allAccounts))).toBeNull();
  });
});
