"""Tests del comparador. No usan Docker: levantan dos mocks y el proxy en puertos locales.
Uso: python3 -m unittest -v scripts/shadow/test_shadow_compare.py   (o desde scripts/shadow/)
"""
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
import shadow_compare as sc  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def call(port, path, method="GET", data=None, timeout=5):
    req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", data=data, method=method,
                                 headers={"Authorization": "Bearer t"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def start_mock(name):
    port = free_port()
    p = subprocess.Popen([sys.executable, os.path.join(HERE, "mock", "server.py")],
                         env={**os.environ, "MOCK_NAME": name, "MOCK_PORT": str(port)})
    for _ in range(50):
        try:
            call(port, "/__stats")
            return p, port
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("mock no arrancó")


class DiffTests(unittest.TestCase):
    def test_iguales(self):
        self.assertEqual(sc.diff({"a": 1, "b": [1, 2]}, {"a": 1, "b": [1, 2]}), [])

    def test_ignora_claves(self):
        self.assertEqual(sc.diff({"a": 1, "ts": 1}, {"a": 1, "ts": 2}, ignore={"ts"}), [])

    def test_valor_distinto_con_ruta(self):
        self.assertEqual(sc.diff({"x": {"y": "a"}}, {"x": {"y": "b"}}),
                         ["$.x.y: 'a' (real) != 'b' (sombra)"])

    def test_tolerancia_numerica(self):
        self.assertEqual(sc.diff({"s": 0.90}, {"s": 0.93}, tol=0.05), [])
        self.assertEqual(len(sc.diff({"s": 0.90}, {"s": 0.99}, tol=0.05)), 1)

    def test_clave_faltante_y_largo_de_lista(self):
        self.assertIn("$.k: solo en real", sc.diff({"k": 1}, {})[0])
        self.assertIn("largo", sc.diff([1], [1, 2])[0])

    def test_bool_no_es_numero(self):
        self.assertEqual(len(sc.diff({"f": True}, {"f": 1})), 1)


class ProxyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pp, cls.primary_port = start_mock("primary")
        cls.sp, cls.shadow_port = start_mock("shadow")
        cls.log = tempfile.NamedTemporaryFile(suffix=".jsonl", delete=False).name
        cls.proxy_port = free_port()
        args = sc.parse_args(["--primary", f"http://127.0.0.1:{cls.primary_port}",
                              "--shadow", f"http://127.0.0.1:{cls.shadow_port}",
                              "--listen", str(cls.proxy_port), "--log", cls.log,
                              "--ignore", "served_by,ts", "--shadow-timeout", "1"])
        cls.server, cls.stats, cls.pending = sc.build_server(args)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        for p in (cls.pp, cls.sp):
            p.terminate()
            p.wait()
        os.unlink(cls.log)

    def setUp(self):
        call(self.primary_port, "/__reset", "POST", b"")
        call(self.shadow_port, "/__reset", "POST", b"")
        call(self.shadow_port, "/__mode?m=ok", "POST", b"")
        self.stats.counts = dict.fromkeys(self.stats.counts, 0)
        open(self.log, "w").close()

    def hit(self, path="/api/llm/v1/x", **kw):
        status, body = call(self.proxy_port, path, **kw)
        for t in list(self.pending):
            t.join(5)
        self.pending.clear()
        return status, body

    def last(self):
        with open(self.log) as f:
            return json.loads(f.readlines()[-1])

    def test_respuestas_equivalentes(self):
        status, body = self.hit()
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body)["served_by"], "primary")  # el cliente ve al real
        self.assertEqual(self.last()["result"], "match")
        self.assertEqual(self.stats.summary()["match"], 1)

    def test_detecta_diferencia_de_body(self):
        call(self.shadow_port, "/__mode?m=diff", "POST", b"")
        self.hit()
        rec = self.last()
        self.assertEqual(rec["result"], "body_mismatch")
        self.assertIn("$.score: 0.9 (real) != 0.1 (sombra)", rec["diffs"])

    def test_detecta_diferencia_de_status(self):
        call(self.shadow_port, "/__mode?m=error", "POST", b"")
        status, _ = self.hit()
        self.assertEqual(status, 200)  # el cliente no se entera
        self.assertEqual(self.last()["result"], "status_mismatch")

    def test_sombra_lenta_no_frena_al_cliente(self):
        call(self.shadow_port, "/__mode?m=slow", "POST", b"")
        t = time.monotonic()
        status, _ = call(self.proxy_port, "/api/llm/v1/x")
        self.assertLess(time.monotonic() - t, 1.0)
        self.assertEqual(status, 200)
        for th in list(self.pending):
            th.join(5)
        self.pending.clear()
        self.assertEqual(self.last()["result"], "shadow_error")  # timeout de 1s

    def test_replica_body_y_marca_shadow(self):
        self.hit("/api/llm/v1/m", method="POST", data=b'{"text":"hola"}')
        got = json.loads(call(self.shadow_port, "/__stats")[1])["received"][0]
        self.assertEqual(got["body"], '{"text":"hola"}')
        self.assertEqual(got["shadow"], "1")
        self.assertEqual(got["authorization"], "Bearer t")

    def test_summary_endpoint(self):
        self.hit()
        s = json.loads(call(self.proxy_port, "/__summary")[1])
        self.assertEqual((s["total"], s["match"], s["match_rate"]), (1, 1, 1.0))


if __name__ == "__main__":
    unittest.main()
