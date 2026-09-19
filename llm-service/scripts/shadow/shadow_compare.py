#!/usr/bin/env python3
"""Proxy comparador para shadow deploy de llm-service (solo librería estándar).

Cada request se reenvía al servicio REAL y esa respuesta vuelve al cliente sin esperar a nada más.
En segundo plano, la misma request se reenvía a la SOMBRA y se comparan status y body.
Reemplaza a nginx en el rol de mirror cuando se quiere comparar respuestas (nginx las descarta).

Uso:
  python3 shadow_compare.py --primary http://llm-service.tail767776.ts.net:8086 \
                            --shadow  http://llm-service-shadow.tail767776.ts.net:8086 \
                            --listen 8080 --ignore requestId,timestamp --tolerance 0.05

  GET /__summary  -> contadores y latencias. Al cortar con Ctrl+C también imprime el resumen.
  Cada comparación se agrega a --log (JSONL, una línea por request).
"""
import argparse
import collections
import http.client
import json
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

HOP_HEADERS = {"connection", "keep-alive", "transfer-encoding", "host", "content-length",
               "proxy-authenticate", "proxy-authorization", "te", "trailer", "upgrade"}
DEFAULT_IGNORE = "timestamp,createdAt,updatedAt,requestId,traceId,ts"


def send(base, method, path, headers, body, timeout):
    """Reenvía una request. Devuelve {status, headers, body, ms, error}."""
    u = urlparse(base)
    start = time.monotonic()
    try:
        conn_cls = http.client.HTTPSConnection if u.scheme == "https" else http.client.HTTPConnection
        conn = conn_cls(u.hostname, u.port, timeout=timeout)
        conn.request(method, path, body=body or None, headers=headers)
        r = conn.getresponse()
        data = r.read()
        conn.close()
        return {"status": r.status, "headers": r.getheaders(), "body": data,
                "ms": round((time.monotonic() - start) * 1000, 1), "error": None}
    except Exception as e:  # noqa: BLE001 - cualquier fallo de red cuenta como error del upstream
        return {"status": None, "headers": [], "body": b"",
                "ms": round((time.monotonic() - start) * 1000, 1), "error": f"{type(e).__name__}: {e}"}


def _is_num(x):
    return isinstance(x, (int, float)) and not isinstance(x, bool)


def diff(a, b, path="$", ignore=frozenset(), tol=0.0):
    """Lista de diferencias entre dos valores JSON. Vacía = equivalentes."""
    if isinstance(a, dict) and isinstance(b, dict):
        out = []
        for k in sorted(set(a) | set(b)):
            if k in ignore:
                continue
            if k not in a:
                out.append(f"{path}.{k}: solo en sombra")
            elif k not in b:
                out.append(f"{path}.{k}: solo en real")
            else:
                out += diff(a[k], b[k], f"{path}.{k}", ignore, tol)
        return out
    if isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            return [f"{path}: largo {len(a)} (real) != {len(b)} (sombra)"]
        out = []
        for i, (x, y) in enumerate(zip(a, b)):
            out += diff(x, y, f"{path}[{i}]", ignore, tol)
        return out
    if _is_num(a) and _is_num(b):
        return [] if abs(a - b) <= tol else [f"{path}: {a!r} (real) != {b!r} (sombra)"]
    return [] if a == b and type(a) is type(b) else [f"{path}: {a!r} (real) != {b!r} (sombra)"]


def compare(primary, shadow, ignore, tol):
    """Compara dos resultados de send(). Devuelve (categoria, diffs)."""
    if shadow["error"]:
        return "shadow_error", [shadow["error"]]
    if primary["error"]:
        return "primary_error", [primary["error"]]
    if primary["status"] != shadow["status"]:
        return "status_mismatch", [f"status {primary['status']} (real) != {shadow['status']} (sombra)"]
    try:
        d = diff(json.loads(primary["body"]), json.loads(shadow["body"]), ignore=ignore, tol=tol)
    except ValueError:  # no es JSON: comparación byte a byte
        d = [] if primary["body"] == shadow["body"] else ["body no-JSON distinto"]
    return ("match", []) if not d else ("body_mismatch", d)


class Stats:
    def __init__(self, log_path):
        self.lock = threading.Lock()
        self.log_path = log_path
        self.counts = {"match": 0, "status_mismatch": 0, "body_mismatch": 0,
                       "shadow_error": 0, "primary_error": 0}
        self.primary_ms, self.shadow_ms = [], []

    def record(self, entry, category, p, s):
        with self.lock:
            self.counts[category] += 1
            self.primary_ms.append(p["ms"])
            if not s["error"]:
                self.shadow_ms.append(s["ms"])
            if self.log_path:
                with open(self.log_path, "a") as f:
                    f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    def summary(self):
        with self.lock:
            total = sum(self.counts.values())
            avg = lambda xs: round(sum(xs) / len(xs), 1) if xs else None  # noqa: E731
            return {"total": total, **self.counts,
                    "match_rate": round(self.counts["match"] / total, 4) if total else None,
                    "avg_primary_ms": avg(self.primary_ms), "avg_shadow_ms": avg(self.shadow_ms)}


def make_handler(args, stats, pending):
    ignore = frozenset(x for x in args.ignore.split(",") if x)

    class Handler(BaseHTTPRequestHandler):
        def _reply(self, status, headers, body):
            self.send_response(status)
            for k, v in headers:
                if k.lower() not in HOP_HEADERS:
                    self.send_header(k, v)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _handle(self):
            if self.path.startswith("/__summary"):
                return self._reply(200, [("Content-Type", "application/json")],
                                   json.dumps(stats.summary()).encode())
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length) if length else b""
            headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_HEADERS}

            primary = send(args.primary, self.command, self.path, headers, body, args.primary_timeout)
            # La sombra arranca en paralelo a la respuesta al cliente; nunca la retrasa.
            t = threading.Thread(target=self._shadow, daemon=True,
                                 args=(self.command, self.path, headers, body, primary))
            pending.append(t)
            t.start()

            if primary["error"]:
                self._reply(502, [("Content-Type", "application/json")],
                            json.dumps({"error": "primary_unreachable"}).encode())
            else:
                self._reply(primary["status"], primary["headers"], primary["body"])

        def _shadow(self, method, path, headers, body, primary):
            shadow = send(args.shadow, method, path, {**headers, "X-Shadow-Request": "1"}, body,
                          args.shadow_timeout)
            category, diffs = compare(primary, shadow, ignore, args.tolerance)
            stats.record({"ts": time.strftime("%Y-%m-%dT%H:%M:%S"), "method": method, "path": path,
                          "result": category, "diffs": diffs,
                          "primary_status": primary["status"], "shadow_status": shadow["status"],
                          "primary_ms": primary["ms"], "shadow_ms": shadow["ms"]},
                         category, primary, shadow)
            mark = "OK  " if category == "match" else "DIFF"
            print(f"[{mark}] {method} {path} {category} real={primary['ms']}ms sombra={shadow['ms']}ms"
                  + (f"\n       " + "\n       ".join(diffs[:5]) if diffs else ""), flush=True)

        do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = _handle

        def log_message(self, *a):
            pass

    return Handler


def build_server(args):
    stats, pending = Stats(args.log), collections.deque(maxlen=1000)  # pending: solo para que los tests esperen
    server = ThreadingHTTPServer(("0.0.0.0", args.listen), make_handler(args, stats, pending))
    return server, stats, pending


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--primary", required=True, help="URL base del servicio real")
    p.add_argument("--shadow", required=True, help="URL base de la sombra")
    p.add_argument("--listen", type=int, default=8080)
    p.add_argument("--ignore", default=DEFAULT_IGNORE, help="claves JSON a ignorar (coma)")
    p.add_argument("--tolerance", type=float, default=0.0, help="diferencia numérica aceptada")
    p.add_argument("--log", default="shadow-compare.jsonl", help="archivo JSONL ('' = no escribir)")
    p.add_argument("--primary-timeout", type=float, default=30)
    p.add_argument("--shadow-timeout", type=float, default=10)
    return p.parse_args(argv)


def main():
    args = parse_args()
    server, stats, _ = build_server(args)
    signal.signal(signal.SIGTERM, lambda *_: threading.Thread(target=server.shutdown).start())
    print(f"Comparador escuchando en :{args.listen}  real={args.primary}  sombra={args.shadow}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    print("\nResumen:", json.dumps(stats.summary(), indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
