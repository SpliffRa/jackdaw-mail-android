import "../../../../logic/app";
import { appGlobal } from "../../../../logic/app";
import { IMAPAccount } from "../../../../logic/Mail/IMAP/IMAPAccount";
import { IMAPFolder } from "../../../../logic/Mail/IMAP/IMAPFolder";
import { DummyMailStorage } from "../../../../logic/Mail/Store/DummyMailStorage";
import { Lock } from "../../../../logic/util/flow/Lock";
import { ArrayColl } from "svelte-collections";
import { expect, test, vi } from "vitest";

function createFolder(): IMAPFolder {
  appGlobal.remoteApp = { createIMAPFlowConnection: () => null };
  let account = new IMAPAccount();
  account.storage = new DummyMailStorage();
  let folder = new IMAPFolder(account);
  folder.path = "Trash";
  folder.name = "Trash";
  folder.dbID = 1;
  (folder as any).haveReadFolder = true;
  return folder;
}

function stubStatus(folder: IMAPFolder, status: Record<string, number>) {
  (folder as any).runCommand = async (callback: (connection: any) => Promise<unknown>) =>
    callback({
      id: "test-connection",
      status: async () => status,
    });
}

test("обновляет IMAP-счётчики после изменений на сервере", async () => {
  let folder = createFolder();
  folder.countTotal = 9;
  folder.countUnread = 6;
  folder.countNewArrived = 4;
  stubStatus(folder, { messages: 3, unseen: 2, recent: 1 });

  await folder.refreshStatus();

  expect(folder.countTotal).toBe(3);
  expect(folder.countUnread).toBe(2);
  // \\Recent belongs to the local "new" marker and is cleared on view.
  expect(folder.countNewArrived).toBe(4);
});

test("не оставляет пустую IMAP-папку в состоянии загрузки", async () => {
  let folder = createFolder();
  folder.countTotal = 9;
  folder.countUnread = 2;
  let staleMessage = folder.newEMail();
  staleMessage.uid = 42;
  staleMessage.dbID = 2;
  folder.messages.add(staleMessage);

  stubStatus(folder, { messages: 0, unseen: 0, recent: 0 });
  (folder as any).fetchUIDList = async () => new ArrayColl<number>();

  let messages = await folder.listMessages();

  expect(messages.isEmpty).toBe(true);
  expect(folder.messages.isEmpty).toBe(true);
  expect(folder.countTotal).toBe(0);
  expect(folder.countUnread).toBe(0);
});

test("сверяет UID после уменьшения счётчика в уже открытой папке", async () => {
  let folder = createFolder();
  folder.countTotal = 5;
  let staleMessage = folder.newEMail();
  (staleMessage as any).uid = 1;
  staleMessage.dbID = 2;
  folder.messages.add(staleMessage as any);

  stubStatus(folder, { messages: 4, unseen: 1, recent: 0 });
  (folder as any).fetchUIDList = async () => new ArrayColl<number>([2, 3, 4, 5]);
  (folder as any).fetchMessageList = async () => ({
    newMessages: new ArrayColl(),
    updatedMessages: new ArrayColl(),
  });

  await folder.listMessages(true);

  expect(folder.messages.contains(staleMessage as any)).toBe(false);
});

test("обновляет счётчики всех известных IMAP-папок", async () => {
  let folder = createFolder();
  folder.countTotal = 9;
  folder.countUnread = 6;
  folder.account.rootFolders.add(folder);

  let connection = {
    id: "test-connection",
    list: async () => [{
      path: "Trash",
      status: { messages: 0, unseen: 0, recent: 0 },
    }],
  };
  (folder.account as any).connection = async () => connection;
  folder.account.connectionLock.set(connection as any, new Lock());

  await folder.account.refreshFolderCounts();

  expect(folder.countTotal).toBe(0);
  expect(folder.countUnread).toBe(0);
});

test("после EXPUNGE сверяет UID даже для первого письма", async () => {
  let folder = createFolder();
  let listMessages = vi.spyOn(folder, "listMessages").mockResolvedValue(new ArrayColl());

  await folder.messageDeletedNotification(1, { id: "test-connection" } as any);

  expect(listMessages).toHaveBeenCalledWith(true);
});

test("полная сверка после удаления не помечает старые письма новыми", async () => {
  let folder = createFolder();
  let existingMessage = folder.newEMail();
  existingMessage.uid = 1;
  folder.messages.add(existingMessage);

  let discoveredMessage = folder.newEMail();
  discoveredMessage.uid = 2;
  stubStatus(folder, { messages: 2, unseen: 0, recent: 0 });
  (folder as any).fetchUIDList = async () => new ArrayColl<number>([1, 2]);
  (folder as any).fetchMessageList = async () => ({
    newMessages: new ArrayColl([discoveredMessage]),
    updatedMessages: new ArrayColl(),
  });

  await folder.listMessages(true);

  expect(discoveredMessage.isNewArrived).toBe(false);
});
