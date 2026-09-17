// app first, to resolve the import cycle around Abstract/Account.ts
import { appGlobal } from "../../../logic/app";
import { Attachment, ContentDisposition } from "../../../logic/Abstract/Attachment";
import { UserError } from "../../../logic/util/util";
import { InMemoryFileReader } from "../util/fileReader";
import { ArrayColl } from "svelte-collections";
import { afterEach, beforeAll, expect, test, vi } from "vitest";

const kContent = new Uint8Array([1, 2, 3, 4]);
const remoteAppBeforeTests = appGlobal.remoteApp;

beforeAll(() => {
  globalThis.FileReader ??= InMemoryFileReader as any;
});

afterEach(() => {
  appGlobal.remoteApp = remoteAppBeforeTests;
});

test("Attachment contents as base64", async () => {
  let attachment = newAttachment();
  expect(await attachment.contentAsBase64()).toBe(Buffer.from(kContent).toString("base64"));
});

test("Attachment whose file is gone from disk reports the filename", async () => {
  let attachment = newAttachment();
  attachment.content.arrayBuffer = () => Promise.reject(new Error("File not found"));
  await expect(attachment.contentAsBase64()).rejects.toThrow(UserError);
  await expect(attachment.contentAsBase64()).rejects.toThrow("agenda.pdf");
});

test("inline related media is not shown as a regular attachment", () => {
  let inline = newAttachment();
  inline.disposition = ContentDisposition.inline;
  inline.related = true;
  let regular = newAttachment();
  regular.disposition = ContentDisposition.attachment;
  regular.related = true;

  let visible = new ArrayColl([inline, regular]).filterObservable(attachment => !attachment.hidden);

  expect(visible.contents).toEqual([regular]);
});

test("opens an attachment after loading and caching it locally", async () => {
  let openFileInNativeApp = vi.fn();
  let loadedAttachment: Attachment;
  let message: any = {
    attachments: new ArrayColl<Attachment>(),
    loadAttachments: async () => {
      message.attachments.replaceAll([loadedAttachment]);
    },
  };
  let sourceAttachment = new Attachment();
  sourceAttachment.message = message;
  sourceAttachment.filename = "image001.png";
  sourceAttachment.contentID = null;
  sourceAttachment.size = 15 * 1024;

  loadedAttachment = new Attachment();
  loadedAttachment.message = message;
  loadedAttachment.filename = sourceAttachment.filename;
  loadedAttachment.contentID = "1";
  loadedAttachment.size = sourceAttachment.size;
  loadedAttachment.content = new File([new Uint8Array([1, 2, 3])], loadedAttachment.filename, { type: "image/png" });
  loadedAttachment.storage = new ArrayColl([{
    supportsAttachments: true,
    saveAttachment: async (attachment: Attachment) => {
      attachment.filepathLocal = "/tmp/jackdaw/image001.png";
    },
  }]);
  message.attachments.add(sourceAttachment);

  appGlobal.remoteApp = { openFileInNativeApp };

  await sourceAttachment.openOSApp();

  expect(openFileInNativeApp).toHaveBeenCalledWith("/tmp/jackdaw/image001.png");
});

function newAttachment(): Attachment {
  let attachment = new Attachment();
  attachment.filename = "agenda.pdf";
  attachment.mimeType = "application/pdf";
  attachment.content = new File([kContent as BlobPart], "agenda.pdf", { type: "application/pdf" });
  attachment.size = kContent.length;
  return attachment;
}
