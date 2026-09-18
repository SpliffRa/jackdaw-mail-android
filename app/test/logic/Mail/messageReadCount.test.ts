import "../../../logic/app";
import { EMail } from "../../../logic/Mail/EMail";
import { Folder } from "../../../logic/Mail/Folder";
import { MailAccount } from "../../../logic/Mail/MailAccount";
import { DummyMailStorage } from "../../../logic/Mail/Store/DummyMailStorage";
import { ArrayColl } from "svelte-collections";
import { expect, test } from "vitest";

test("clears the new-arrival badge when the last unread message is read", async () => {
  let folder = new Folder(new MailAccount());
  let message = new EMail(folder);
  folder.countUnread = 1;
  folder.countNewArrived = 1;

  await message.markRead(true);

  expect(message.isRead).toBe(true);
  expect(folder.countUnread).toBe(0);
  expect(folder.countNewArrived).toBe(0);
});

test("marking a message unread updates only the unread count", async () => {
  let folder = new Folder(new MailAccount());
  let message = new EMail(folder);
  message.isRead = true;

  await message.markRead(false);

  expect(message.isRead).toBe(false);
  expect(folder.countUnread).toBe(1);
  expect(folder.countNewArrived).toBe(0);
});

test("перенос непрочитанного письма обновляет счётчики обеих папок", async () => {
  let account = new MailAccount();
  account.storage = new DummyMailStorage();
  let source = new Folder(account);
  let target = new Folder(account);
  source.countTotal = 1;
  source.countUnread = 1;
  let message = new EMail(source);
  source.messages.add(message);
  (target as any).moveOrCopyMessagesOnServer = async () => {};

  await target.moveMessagesHere(new ArrayColl([message]));

  expect(source.countTotal).toBe(0);
  expect(source.countUnread).toBe(0);
  expect(target.countTotal).toBe(1);
  expect(target.countUnread).toBe(1);
});

test("перенос сразу обновляет исходный счётчик до ответа сервера", async () => {
  let account = new MailAccount();
  account.storage = new DummyMailStorage();
  let source = new Folder(account);
  let target = new Folder(account);
  source.countTotal = 1;
  source.countUnread = 1;
  source.countNewArrived = 1;
  let message = new EMail(source);
  message.isNewArrived = true;
  source.messages.add(message);

  let finishServerMove: () => void;
  let serverMove = new Promise<void>(resolve => finishServerMove = resolve);
  (target as any).moveOrCopyMessagesOnServer = async () => serverMove;

  let move = target.moveMessagesHere(new ArrayColl([message]));
  await Promise.resolve();

  expect(source.countTotal).toBe(0);
  expect(source.countUnread).toBe(0);
  expect(source.countNewArrived).toBe(0);
  expect(target.countTotal).toBe(1);
  expect(target.countUnread).toBe(1);

  finishServerMove!();
  await move;
});
