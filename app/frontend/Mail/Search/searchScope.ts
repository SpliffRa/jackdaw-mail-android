import type { Folder } from "../../../logic/Mail/Folder";
import type { MailAccount } from "../../../logic/Mail/MailAccount";

/** Возвращает конкретный ящик, представленный текущим видом почты.
 * Объединённый вид «Все аккаунты» намеренно сохраняет глобальный поиск. */
export function currentMailSearchAccount(
  selectedAccount: MailAccount | null | undefined,
  selectedFolder: Folder | null | undefined,
): MailAccount | null {
  let account = selectedFolder?.account ?? selectedAccount;
  return account?.protocol == "all" ? null : account ?? null;
}
