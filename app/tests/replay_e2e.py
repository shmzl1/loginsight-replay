"""Test the packaged application through its public HTTP interface."""
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import unittest
from contextlib import contextmanager
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app" / "target"
PLUGIN = ROOT / "plugins" / "jsonl" / "target" / "loginsight-jsonl-0.1.0-SNAPSHOT.jar"
MINUTE = "2026-05-12T08:00:00Z"


@contextmanager
def server(plugin=None):
    classpath = os.pathsep.join([str(APP / "loginsight-app-0.1.0-SNAPSHOT.jar"), str(APP / "lib" / "*")])
    command = ["java", "-cp", classpath, "org.loginsight.app.ReplayServer", "0"]
    if plugin:
        command.append(str(plugin))
    with subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, text=True) as process:
        output = queue.Queue()
        threading.Thread(target=lambda: output.put(process.stdout.readline()), daemon=True).start()
        try:
            address = output.get(timeout=15).strip()
            if not address.startswith("http://127.0.0.1:"):
                raise RuntimeError(f"Replay server failed to start: {address}")
            yield address
        finally:
            process.terminate()
            process.wait(timeout=10)


def query(address, route, **data):
    request = Request(address + "/api/" + route, json.dumps(data, ensure_ascii=False).encode("utf-8"),
                      {"Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=5) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        with error:
            return error.code, json.load(error)


def upload(address, file, format_name):
    content = (ROOT / "samples" / file).read_text(encoding="utf-8")
    return query(address, "import", file=file, format=format_name, content=content)


class ReplayEndToEnd(unittest.TestCase):
    def assert_replay(self, address, count, lines):
        status, result = query(address, "buckets")
        self.assertEqual((status, result), (200, {"errorBuckets": [{"minuteStart": MINUTE, "errorCount": count}]}))
        self.assertEqual(query(address, "requests", minuteStart=MINUTE), (200, {"failedRequestIds": ["R-17"]}))
        status, result = query(address, "timeline", requestId="R-17")
        self.assertEqual(status, 200)
        events = result["timeline"]
        self.assertEqual([(event["sourceFile"], event["lineNumber"]) for event in events], lines)
        self.assertEqual(len({event["sourceId"] for event in events}), len({file for file, _ in lines}))
        for event in events:
            text = (ROOT / "samples" / event["sourceFile"]).read_text(encoding="utf-8")
            self.assertEqual(event["rawLine"], text.splitlines()[event["lineNumber"] - 1])

    def test_plugin_off_and_invalid_import(self):
        with server() as address:
            for route, text in [("/", "请求时间线"), ("/app.js", "event.rawLine"), ("/app.css", ".raw")]:
                with urlopen(address + route, timeout=5) as response:
                    self.assertIn(text, response.read().decode("utf-8"))
            self.assertEqual(upload(address, "campus.txt", "text")[0], 200)
            status, error = upload(address, "quota.jsonl", "jsonl")
            self.assertEqual(status, 400)
            self.assertIn("jsonl", error["error"])
            first_line = (ROOT / "samples/campus.txt").read_text(encoding="utf-8").splitlines()[0]
            status, error = query(address, "import", file="bad.txt", format="text",
                                  content=first_line + "\ninvalid")
            self.assertEqual(status, 400)
            self.assertIn("bad.txt 第 2 行", error["error"])
            self.assert_replay(address, 1, [("campus.txt", n) for n in (1, 3, 5)])

    def test_plugin_on(self):
        with server(PLUGIN) as address:
            self.assertEqual(upload(address, "campus.txt", "text")[0], 200)
            self.assertEqual(upload(address, "quota.jsonl", "jsonl")[0], 200)
            self.assert_replay(address, 2, [
                ("campus.txt", 1), ("campus.txt", 3), ("quota.jsonl", 1),
                ("quota.jsonl", 2), ("campus.txt", 5)
            ])


if __name__ == "__main__":
    unittest.main()
