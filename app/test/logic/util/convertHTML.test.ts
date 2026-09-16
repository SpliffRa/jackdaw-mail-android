// @vitest-environment happy-dom

import { describe, expect, it } from "vitest";
import { linkifyPhoneNumbers } from "../../../logic/util/convertHTML";
import { kMailImageSourceAttribute } from "../../../logic/Mail/mailImage";

function parse(html: string): Document {
  return new DOMParser().parseFromString(html, "text/html");
}

describe("mail HTML rendering helpers", () => {
  it("does not lose the preserved source of a blocked external image", () => {
    let source = "https://cdn.example.test/signature/logo.png";
    let doc = parse(`<p><img src="" data-jackdaw-image-src="${source}" alt="Company logo"></p>`);
    let image = doc.querySelector("img");

    expect(image?.getAttribute("src")).toBe("");
    expect(image?.getAttribute(kMailImageSourceAttribute)).toBe(source);
  });

  it("turns formatted phone numbers into tel links without touching IDs and dates", () => {
    let doc = parse(linkifyPhoneNumbers(`
      <p>Телефон: +7 (495) 775-42-75 доб. 1107</p>
      <p>Мобильный: +7 977 459 65 29</p>
      <p>Заказ 777029070 от 2026-09-16</p>
    `));
    let links = [...doc.querySelectorAll('a[href^="tel:"]')];

    expect(links).toHaveLength(2);
    expect(links[0].getAttribute("href")).toBe("tel:+74957754275;ext=1107");
    expect(links[1].getAttribute("href")).toBe("tel:+79774596529");
    expect(doc.body.textContent).toContain("777029070");
    expect(doc.body.querySelectorAll('a[href*="777029070"]')).toHaveLength(0);
    expect(doc.body.textContent).toContain("2026-09-16");
  });

  it("does not wrap phone text that is already inside a link", () => {
    let doc = parse(linkifyPhoneNumbers('<p><a href="https://example.test/call">+7 495 775 42 75</a></p>'));

    expect(doc.querySelectorAll('a[href^="tel:"]')).toHaveLength(0);
    expect(doc.querySelectorAll('a[href="https://example.test/call"]')).toHaveLength(1);
  });
});
