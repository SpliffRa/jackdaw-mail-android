import { afterEach, expect, test, vi } from "vitest";
import { appGlobal } from "../../../logic/app";
import { OAuth2 } from "../../../logic/Auth/OAuth2";
import { OAuth2Localhost } from "../../../logic/Auth/UI/OAuth2Localhost";

let originalRemoteApp: any;

afterEach(() => {
  appGlobal.remoteApp = originalRemoteApp;
  vi.restoreAllMocks();
});

function setup() {
  let route: ((url: string) => Promise<string>) | undefined;
  let server = {
    start: vi.fn().mockResolvedValue(undefined),
    get: vi.fn(async (_path: string, callback: (url: string) => Promise<string>) => {
      route = callback;
    }),
    close: vi.fn().mockResolvedValue(undefined),
  };
  let remoteApp = {
    newHTTPServer: vi.fn().mockResolvedValue(server),
  };
  let account = { username: "galkynnikita@gmail.com" } as any;
  let auth = new OAuth2(account, "https://example.test/token", "https://example.test/login",
    "https://example.test/callback", "mail", "client");
  vi.spyOn(auth, "getAuthURL").mockResolvedValue("https://example.test/login");
  vi.spyOn(auth, "isAuthDoneURL").mockResolvedValue(true);
  vi.spyOn(auth, "getAuthCodeFromDoneURL").mockReturnValue("oauth-code");
  originalRemoteApp = appGlobal.remoteApp;
  appGlobal.remoteApp = remoteApp;
  let login = new OAuth2Localhost(auth);
  login.loginURLCallback = vi.fn().mockResolvedValue(undefined);
  return { auth, login, remoteApp, server, getRoute: () => route };
}

test("передаёт Google callback в приложение и закрывает loopback-сервер", async () => {
  let { login, auth, server, getRoute } = setup();
  let loginResult = login.login();
  await vi.waitFor(() => expect(server.get).toHaveBeenCalledOnce());
  let route = getRoute();
  if (!route) {
    throw new Error("OAuth callback route was not registered");
  }

  await expect(route("/login-success?code=test-code&state=test-state")).resolves.toContain("Login successful");
  await expect(loginResult).resolves.toBe("oauth-code");
  expect(auth.isAuthDoneURL).toHaveBeenCalledWith("http://127.0.0.1:5460/login-success?code=test-code&state=test-state");
  expect(server.close).toHaveBeenCalledOnce();
});

test("отмена во время ожидания callback закрывает loopback-сервер", async () => {
  let { login, server } = setup();
  let loginResult = login.login();
  await vi.waitFor(() => expect(server.get).toHaveBeenCalledOnce());

  login.abort();

  await expect(loginResult).rejects.toThrow("Login aborted by user");
  expect(server.close).toHaveBeenCalledOnce();
});
