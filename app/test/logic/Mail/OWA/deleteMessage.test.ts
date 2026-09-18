import "../../../../logic/app";
import { appGlobal } from "../../../../logic/app";
import { OWAAccount } from "../../../../logic/Mail/OWA/OWAAccount";
import { DummyMailStorage } from "../../../../logic/Mail/Store/DummyMailStorage";
import { DeleteStrategy } from "../../../../logic/Mail/MailAccount";
import { SpecialFolder } from "../../../../logic/Mail/Folder";
import { expect, test } from "vitest";

test("очистка корзины удаляет элементы календаря без отправки отмен", async () => {
  appGlobal.remoteApp = { OWA: {} };
  let account = new OWAAccount();
  account.storage = new DummyMailStorage();
  let folder = account.newFolder();
  folder.specialFolder = SpecialFolder.Trash;
  folder.releaseDeletionAfterGracePeriod = () => {};

  let request: any;
  (account as any).callOWA = async (nextRequest: any) => {
    request = nextRequest;
    return { ResponseClass: "Success", ResponseCode: "NoError" };
  };

  let message = folder.newEMail();
  message.itemID = "calendar-item";
  folder.messages.add(message);
  folder.countTotal = 1;

  await folder.clearFolder();

  expect(request.action).toBe("DeleteItem");
  expect(request.Body.DeleteType).toBe("HardDelete");
  expect(request.Body.SendMeetingCancellations).toBe("SendToNone");
  expect(request.Body.SuppressReadReceipts).toBe(true);
  expect(folder.messages.isEmpty).toBe(true);
});

test("очистка корзины OWA удаляет письма одним пакетным запросом", async () => {
  appGlobal.remoteApp = { OWA: {} };
  let account = new OWAAccount();
  account.storage = new DummyMailStorage();
  let folder = account.newFolder();
  folder.specialFolder = SpecialFolder.Trash;
  folder.releaseDeletionAfterGracePeriod = () => {};

  let requests: any[] = [];
  let progress: any[] = [];
  (account as any).callOWA = async (request: any) => {
    requests.push(request);
    return {
      ResponseMessages: {
        Items: request.Body.ItemIds.map(() => ({
          ResponseClass: "Success",
          ResponseCode: "NoError",
        })),
      },
    };
  };

  for (let id of ["message-1", "message-2", "message-3"]) {
    let message = folder.newEMail();
    message.itemID = id;
    folder.messages.add(message);
  }
  folder.countTotal = 3;
  folder.subscribe(() => progress.push(folder.clearProgress));

  await folder.clearFolder();

  expect(requests).toHaveLength(1);
  expect(requests[0].Body.ItemIds.map((item: any) => item.Id)).toEqual([
    "message-1", "message-2", "message-3",
  ]);
  expect(requests[0].Body.DeleteType).toBe("HardDelete");
  expect(folder.messages.isEmpty).toBe(true);
  expect(folder.countTotal).toBe(0);
  expect(folder.countUnread).toBe(0);
  expect(progress).toContainEqual({ phase: "deleting", completed: 3, total: 3 });
});

test("очистка корзины OWA принудительно загружает полный список при неполном кеше", async () => {
  appGlobal.remoteApp = { OWA: {} };
  let account = new OWAAccount();
  account.storage = new DummyMailStorage();
  let folder = account.newFolder();
  folder.specialFolder = SpecialFolder.Trash;
  folder.releaseDeletionAfterGracePeriod = () => {};

  let requests: any[] = [];
  (account as any).callOWA = async (request: any) => {
    requests.push(request);
    return {
      ResponseMessages: {
        Items: request.Body.ItemIds.map(() => ({
          ResponseClass: "Success",
          ResponseCode: "NoError",
        })),
      },
    };
  };

  let cached = folder.newEMail();
  cached.itemID = "message-1";
  folder.messages.add(cached);
  folder.countTotal = 3;
  let listMessagesArgs: any[] = [];
  (folder as any).listMessages = async (...args: any[]) => {
    listMessagesArgs.push(args);
    for (let id of ["message-2", "message-3"]) {
      let message = folder.newEMail();
      message.itemID = id;
      folder.messages.add(message);
    }
    return folder.messages;
  };

  await folder.clearFolder();

  expect(listMessagesArgs).toEqual([[false, true]]);
  expect(requests).toHaveLength(1);
  expect(requests[0].Body.ItemIds.map((item: any) => item.Id)).toEqual([
    "message-1", "message-2", "message-3",
  ]);
  expect(folder.clearProgress).toBeNull();
  expect(folder.messages.isEmpty).toBe(true);
});

test("удаление письма через OWA сохраняет параметр отмен встреч", async () => {
  appGlobal.remoteApp = { OWA: {} };
  let account = new OWAAccount();
  account.storage = new DummyMailStorage();
  let folder = account.newFolder();
  folder.releaseDeletionAfterGracePeriod = () => {};

  let request: any;
  (account as any).callOWA = async (nextRequest: any) => {
    request = nextRequest;
    return { ResponseClass: "Success", ResponseCode: "NoError" };
  };

  let message = folder.newEMail();
  message.itemID = "message";

  await message.deleteMessageOnServer(DeleteStrategy.MoveToTrash);

  expect(request.Body.DeleteType).toBe("MoveToDeletedItems");
  expect(request.Body.SendMeetingCancellations).toBe("SendToNone");
});
