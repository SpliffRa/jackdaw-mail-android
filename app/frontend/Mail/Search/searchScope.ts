import type { Folder } from "../../../logic/Mail/Folder";
import type { MailAccount } from "../../../logic/Mail/MailAccount";
import type { SearchEMail } from "../../../logic/Mail/Store/SearchEMail";

/** Возвращает конкретный ящик, представленный текущим видом почты.
 * Объединённый вид «Все аккаунты» намеренно сохраняет глобальный поиск. */
export function currentMailSearchAccount(
  selectedAccount: MailAccount | null | undefined,
  selectedFolder: Folder | null | undefined,
): MailAccount | null {
  let account = selectedFolder?.account ?? selectedAccount;
  return account?.protocol == "all" ? null : account ?? null;
}

export function syncSearchToCurrentMailbox(
  search: SearchEMail,
  selectedAccount: MailAccount | null | undefined,
  selectedFolder: Folder | null | undefined,
): boolean {
  let account = currentMailSearchAccount(selectedAccount, selectedFolder);
  if (search.account == account &&
      (!search.folder || search.folder.account == account)) {
    return false;
  }
  let accountChanged = search.account != account;
  search.account = account;
  if (accountChanged || (search.folder && search.folder.account != account)) {
    search.folder = null;
  }
  return true;
}
