"""Mock de llm-service para probar el mirror de nginx.

Responde a cualquier ruta con {"served_by": NAME}, registra lo que recibe y expone:
  GET  /__stats          -> requests recibidos
  POST /__reset          -> borra el registro
  POST /__mode?m=ok|slow|error|diff  -> cambia el comportamiento
       (slow = 5s de demora, error = 500, diff = devuelve score distinto)
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

NAME = os.environ.get("MOCK_NAME", "mock")
received = []
mode = "ok"
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle(self):
        global mode
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode() if length else ""

        if url.path == "/__stats":
            with lock:
                return self._send(200, {"name": NAME, "mode": mode, "received": list(received)})
        if url.path == "/__reset":
            with lock:
                received.clear()
            return self._send(200, {"ok": True})
        if url.path == "/__mode":
            with lock:
                mode = parse_qs(url.query).get("m", ["ok"])[0]
            return self._send(200, {"mode": mode})

        with lock:
            received.append({
                "method": self.command,
                "path": self.path,
                "body": body,
                "authorization": self.headers.get("Authorization"),
                "shadow": self.headers.get("X-Shadow-Request"),
            })
            current = mode

        if current == "slow":
            time.sleep(5)
        if current == "error":
            return self._send(500, {"served_by": NAME, "error": "boom"})
        self._send(200, {"served_by": NAME, "score": 0.1 if current == "diff" else 0.9,
                         "verdict": "ok", "ts": time.time()})

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = _handle

    def log_message(self, *args):
        pass


def make_server(port=8086):
    return ThreadingHTTPServer(("0.0.0.0", port), Handler)


if __name__ == "__main__":
    make_server(int(os.environ.get("MOCK_PORT", "8086"))).serve_forever()
