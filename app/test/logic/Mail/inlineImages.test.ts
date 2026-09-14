// @vitest-environment happy-dom
import "../../../logic/app";
import { EMail } from "../../../logic/Mail/EMail";
import { RawFilesAttachment } from "../../../logic/Mail/Store/RawFilesAttachment";
import { ArrayColl } from "svelte-collections";
import { expect, test, vi } from "vitest";
import { addCID } from "../../../logic/Mail/EMail";

test("replaces cid image sources with matching inline attachments", async () => {
  let attachment = {
    contentID: "<image001@example.com>",
    content: new File(["image"], "image.png", { type: "image/png" }),
    hidden: false,
  };
  let email = {
    attachments: new ArrayColl([attachment]),
    loadAttachments: vi.fn(),
  } as any;

  let html = `<html><body><img src="cid:image001%40example.com"></body></html>`;
  let result = await addCID(html, email);

  expect(result).toContain('src="data:image/png;base64,aW1hZ2U="');
  expect(result).not.toContain("cid:image001");
  expect(attachment.hidden).toBe(true);
});

test("loads inline images from MIME when attachment metadata is empty", async () => {
  let account = {
    contentStorage: new ArrayColl([new RawFilesAttachment()]),
    errorCallback: vi.fn(),
    isMyEMailAddress: () => false,
  } as any;
  let email = new EMail({ account } as any);
  email.mime = new TextEncoder().encode(`From: a@example.com\r\n\
To: b@example.com\r\n\
MIME-Version: 1.0\r\n\
Content-Type: multipart/related; boundary="x"\r\n\r\n\
--x\r\n\
Content-Type: text/html; charset=utf-8\r\n\r\n\
<html><body><img src="CID:image001@example.com"></body></html>\r\n\
--x\r\n\
Content-Type: image/png\r\n\
Content-ID: <image001@example.com>\r\n\
Content-Disposition: inline; filename="image.png"\r\n\
Content-Transfer-Encoding: base64\r\n\r\n\
iVBORw0KGgo=\r\n\
--x--\r\n`);

  await email.loadAttachments();
  let result = await addCID(
    `<html><body><img src="CID:image001@example.com"></body></html>`,
    email,
  );

  expect(result).toContain("data:image/png;base64,iVBORw0KGgo=");
  expect(email.attachments.first.content).toBeTruthy();
});
