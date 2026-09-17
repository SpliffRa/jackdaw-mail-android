import express from 'express';

export class HTTPServer {
  app: express;
  httpServer: any;
  async start(port: number) {
    this.app = new express();
    return new Promise<void>((resolve, reject) => {
      let server = this.app.listen(port, "127.0.0.1");
      this.httpServer = server;
      let started = false;
      server.once("listening", () => {
        started = true;
        resolve();
      });
      server.once("error", (ex: Error) => {
        if (started) {
          return;
        }
        if (this.httpServer === server) {
          this.httpServer = undefined;
        }
        reject(ex);
      });
    });
  }
  get(path: string, callback: (url: string) => string | Promise<string>) {
    this.app.get(path, (req, res, next) => {
      Promise.resolve(callback(req?.url ?? ""))
        .then(responseHTML => res.send(responseHTML))
        .catch(next);
    });
  }
  close() {
    let server = this.httpServer;
    this.httpServer = undefined;
    if (!server) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve, reject) => {
      try {
        server.close((ex?: Error) => ex ? reject(ex) : resolve());
      } catch (ex) {
        reject(ex);
      }
    });
  }
}
