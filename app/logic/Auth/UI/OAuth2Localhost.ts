import { OAuth2UI } from "./OAuth2UI";
import { appGlobal } from "../../app";
import { k1MinuteMS } from "../../../frontend/Util/date";
import { UserCancelled, UserError, assert, type URLString } from "../../util/util";
import { gt } from "../../../l10n/l10n";
import { kJackdawOAuthBrowserRedirectURL } from "../OAuth2Secrets";

const kOAuthSuccessHTML = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Jackdaw Mail</title></head>
<body style="font-family:sans-serif;text-align:center;padding:48px">
<h2>Jackdaw Mail</h2><p>Login successful. You can close this tab and return to the app.</p>
</body></html>`;
const kOAuthWaitingHTML = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Jackdaw Mail</title></head>
<body style="font-family:sans-serif;text-align:center;padding:48px">
<h2>Jackdaw Mail</h2><p>Authentication is still in progress. Return to the app after signing in.</p>
</body></html>`;

type OAuth2CallbackServer = {
  start: (port: number) => Promise<void>;
  get: (path: string, callback: (url: URLString) => string | Promise<string>) => Promise<void> | void;
  close: () => Promise<void> | void;
};

/**
 * Starts a local web server on http://localhost, returns the login start URL,
 * opens it in a browser, and waits for the redirect to /login-success?code=...
 */
export class OAuth2Localhost extends OAuth2UI {
  /** Will be called when a login URL is ready. Load this URL into the browser. */
  loginURLCallback: (url: URLString) => Promise<void>;
  protected onAbort?: () => void;

  /** Fixed loopback port for providers that require a registered redirect URI. */
  protected callbackPort(): number {
    let match = kJackdawOAuthBrowserRedirectURL.match(/:(\d+)\//);
    return match ? Number(match[1]) : 5460;
  }

  async login(): Promise<string> {
    assert(this.loginURLCallback, "Need URL callback");
    this.abort();
    let port = this.callbackPort();
    let doneURL = `http://127.0.0.1:${port}/login-success`;
    let server: OAuth2CallbackServer | null = null;

    return await new Promise((resolve, reject) => {
      let settled = false;
      let killTimeout: ReturnType<typeof setTimeout> | undefined;
      const closeServer = async () => {
        let currentServer = server;
        server = null;
        if (currentServer) {
          try {
            await currentServer.close();
          } catch {
            // Сервер мог уже закрыться после ошибки запуска или гонки callback.
          }
        }
      };
      const finish = (fn: () => void) => {
        if (settled) {
          return;
        }
        settled = true;
        if (killTimeout) {
          clearTimeout(killTimeout);
        }
        this.onAbort = undefined;
        fn();
      };

      const minutes = 15;
      killTimeout = setTimeout(() => {
        finish(() => {
          void closeServer();
          reject(new UserError(gt`Authentication page timed out after ${minutes} minutes`));
        });
      }, minutes * k1MinuteMS);

      this.onAbort = () => {
        finish(() => {
          void closeServer();
          reject(new UserCancelled(gt`Login aborted by user`));
        });
      };

      void (async () => {
        try {
          server = await appGlobal.remoteApp.newHTTPServer();
          await server.start(port);
          if (settled) {
            await closeServer();
            return;
          }

          // Register the handler BEFORE opening the browser — otherwise a fast
          // Google redirect can hit the server before the route exists.
          await server.get("/login-success", async (urlPath: URLString) => {
            let url = new URL(urlPath, doneURL).toString();
            let params = Object.fromEntries(new URL(url).searchParams);
            if (!params.code && !params.error) {
              return kOAuthWaitingHTML;
            }
            try {
              if (!await this.oAuth2.isAuthDoneURL(url)) {
                return kOAuthWaitingHTML;
              }
              let authCode = this.oAuth2.getAuthCodeFromDoneURL(url);
              finish(() => {
                resolve(authCode);
                void closeServer();
              });
            } catch (ex) {
              finish(() => {
                reject(ex);
                void closeServer();
              });
            }
            return kOAuthSuccessHTML;
          });
          if (settled) {
            await closeServer();
            return;
          }

          let loginURL = await this.oAuth2.getAuthURL(doneURL);
          if (settled) {
            await closeServer();
            return;
          }
          await this.loginURLCallback(loginURL);
        } catch (ex) {
          finish(() => {
            void closeServer();
            reject(ex);
          });
        }
      })();
    });
  }

  abort() {
    this.onAbort?.();
  }
}
