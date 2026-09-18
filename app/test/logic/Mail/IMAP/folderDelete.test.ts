import "../../../../logic/app";
import { appGlobal } from "../../../../logic/app";
import { IMAPAccount } from "../../../../logic/Mail/IMAP/IMAPAccount";
import { IMAPFolder } from "../../../../logic/Mail/IMAP/IMAPFolder";
import { SpecialFolder } from "../../../../logic/Mail/Folder";
import { DeleteStrategy } from "../../../../logic/Mail/MailAccount";
import { DummyMailStorage } from "../../../../logic/Mail/Store/DummyMailStorage";
import { expect, test, vi } from "vitest";

function createFolder(): IMAPFolder {
  appGlobal.remoteApp = { createIMAPFlowConnection: () => null };
  let account = new IMAPAccount();
  account.storage = new DummyMailStorage();
  let folder = new IMAPFolder(account);
  folder.path = "template";
  folder.name = "template";
  folder.dbID = 1;
  (folder as any).haveReadFolder = true;
  account.rootFolders.add(folder);
  return folder;
}

test("не удаляет IMAP-папку локально, если сервер отклонил DELETE", async () => {
  let folder = createFolder();
  let serverDelete = vi.fn(async () => {
    throw new Error("NO [CANNOT] folder cannot be deleted");
  });
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({ mailboxDelete: serverDelete });

  await expect(folder.deleteIt()).rejects.toThrow("folder cannot be deleted");

  expect(serverDelete).toHaveBeenCalledWith("template");
  expect(folder.account.rootFolders.contains(folder)).toBe(true);
});

test("удаляет IMAP-папку локально только после успешного DELETE", async () => {
  let folder = createFolder();
  let serverDelete = vi.fn(async () => undefined);
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({ mailboxDelete: serverDelete });

  await folder.deleteIt();

  expect(serverDelete).toHaveBeenCalledWith("template");
  expect(folder.account.rootFolders.contains(folder)).toBe(false);
});

test("уменьшает unread-счётчик при окончательном удалении письма", async () => {
  let folder = createFolder();
  folder.countTotal = 1;
  folder.countUnread = 1;
  folder.countNewArrived = 1;
  let message = folder.newEMail();
  message.uid = 7;
  message.isNewArrived = true;
  folder.messages.add(message);
  let serverDelete = vi.fn(async () => undefined);
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({ id: "test-connection", messageDelete: serverDelete });

  await message.deleteMessage(DeleteStrategy.DeleteImmediately);

  expect(serverDelete).toHaveBeenCalledWith(7, { uid: true });
  expect(folder.countTotal).toBe(0);
  expect(folder.countUnread).toBe(0);
  expect(folder.countNewArrived).toBe(0);
});

test("очищает специальную IMAP-папку одной серверной операцией", async () => {
  let folder = createFolder();
  folder.specialFolder = SpecialFolder.Trash;
  folder.countTotal = 2;
  folder.countUnread = 1;
  folder.countNewArrived = 1;
  let firstMessage = folder.newEMail();
  firstMessage.uid = 7;
  let secondMessage = folder.newEMail();
  secondMessage.uid = 8;
  secondMessage.isRead = true;
  folder.messages.add(firstMessage);
  folder.messages.add(secondMessage);
  let serverDelete = vi.fn(async () => true);
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({ messageDelete: serverDelete });

  await folder.deleteAllMessages();

  expect(serverDelete).toHaveBeenCalledWith({ all: true }, { uid: true });
  expect(folder.messages.isEmpty).toBe(true);
  expect(folder.countTotal).toBe(0);
  expect(folder.countUnread).toBe(0);
  expect(folder.countNewArrived).toBe(0);
});

test("очищает специальную IMAP-папку при пустом локальном списке", async () => {
  let folder = createFolder();
  folder.specialFolder = SpecialFolder.Spam;
  let serverDelete = vi.fn(async () => true);
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({ messageDelete: serverDelete });

  await folder.clearFolder();

  expect(serverDelete).toHaveBeenCalledWith({ all: true }, { uid: true });
});
