import { expect, test, vi } from "vitest";
import { HTTPServer } from "../../../desktop/backend/HTTPServer";

test("дожидается асинхронного ответа callback перед отправкой HTTP-ответа", async () => {
  let server = new HTTPServer();
  await server.start(0);
  try {
    let port = server.httpServer.address().port;
    await server.get("/login-success", async url => {
      await Promise.resolve();
      expect(url).toBe("/login-success?code=test-code");
      return "callback completed";
    });

    let response = await fetch(`http://127.0.0.1:${port}/login-success?code=test-code`);
    await expect(response.text()).resolves.toBe("callback completed");
  } finally {
    await server.close();
  }
});

test("возвращает ошибку, если callback-порт уже занят", async () => {
  let occupiedServer = new HTTPServer();
  await occupiedServer.start(0);
  let port = occupiedServer.httpServer.address().port;
  let contender = new HTTPServer();
  try {
    await expect(contender.start(port)).rejects.toMatchObject({ code: "EADDRINUSE" });
  } finally {
    await contender.close();
    await occupiedServer.close();
  }
});

