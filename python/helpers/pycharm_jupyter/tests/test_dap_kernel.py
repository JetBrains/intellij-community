# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import ast
import base64
import io
import os
from pathlib import Path
import queue
import subprocess
import sys
import tempfile
import time
import unittest
import xml.etree.ElementTree as ET
from urllib.parse import unquote

import debugpy
from jupyter_client import KernelManager
from jupyter_client.kernelspec import KernelSpec
import numpy as np
import pandas as pd
from PIL import Image


class DapKernelViewerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="pycharm-dap-test-")
        self.addCleanup(self.directory.cleanup)
        env = dict(os.environ)
        env["PYTHONPATH"] = os.pathsep.join(
            str(Path(path or os.getcwd()).resolve()) for path in sys.path
        )
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        env["PYDEVD_DISABLE_FILE_VALIDATION"] = "1"
        directories = (
            "IPYTHONDIR", "JUPYTER_CONFIG_DIR", "JUPYTER_RUNTIME_DIR", "TMPDIR",
        )
        for name in directories:
            env[name] = self.directory.name
        self.kernel = KernelManager(
            connection_file=str(Path(self.directory.name) / "connection.json"),
        )
        self.kernel._kernel_spec = KernelSpec(
            argv=[
                sys.executable, "-B", "-m", "ipykernel_launcher",
                "-f", "{connection_file}",
            ],
            display_name="Data viewer test", language="python",
        )
        self.kernel.start_kernel(
            cwd=self.directory.name, env=env,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        self.addCleanup(self.kernel.shutdown_kernel, now=True)
        self.client = self.kernel.blocking_client()
        self.client.start_channels()
        self.addCleanup(self.client.stop_channels)
        self.client.wait_for_ready(timeout=30)
        self.sequence = 0

    def receive(self, channel, predicate, timeout=30):
        deadline = time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise queue.Empty("The expected kernel message did not arrive")
            message = channel(timeout=remaining)
            if predicate(message):
                return message

    def debug(self, command, arguments=None):
        self.sequence += 1
        request = self.client.session.msg("debug_request", {
            "seq": self.sequence, "type": "request", "command": command,
            "arguments": arguments or {},
        })
        self.client.control_channel.send(request)
        message = self.receive(self.client.get_control_msg, lambda message:
            message["parent_header"].get("msg_id") == request["header"]["msg_id"])
        result = message["content"]
        self.assertTrue(result["success"], result)
        return result.get("body", {})

    def shell_reply(self, message_id, timeout=30):
        message = self.receive(self.client.get_shell_msg, lambda message:
            message["parent_header"].get("msg_id") == message_id, timeout)
        self.assertEqual("ok", message["content"]["status"], message["content"])

    def evaluate(self, expression):
        return self.debug("evaluate", {
            "expression": expression, "frameId": self.frame_id, "context": "clipboard",
        })["result"]

    def viewer(self, method, expression, *arguments, success=True):
        args = ", ".join([repr(expression)] + [repr(arg) for arg in arguments])
        call = (
            "__import__('pycharm_jupyter.dap', fromlist=[{0!r}])"
            ".{0}({1}, globals(), locals())"
        ).format(method, args)
        response = ast.literal_eval(self.evaluate(call))
        prefix = "PYCHARM_DATAVIEW_1:{}:".format("OK" if success else "ERROR")
        self.assertTrue(response.startswith(prefix), response[:200])
        return base64.b64decode(response[len(prefix):], validate=True).decode("utf-8")

    def test_viewers_work_while_the_kernel_shell_is_blocked(self):
        setup = """import debugpy
import numpy as np
import pandas as pd
df = pd.DataFrame({'global_only': [1]})
"""
        self.shell_reply(self.client.execute(setup, store_history=False))
        self.debug("initialize", {
            "adapterID": "python", "clientID": "PY-88940",
            "pathFormat": "path", "linesStartAt1": True, "columnsStartAt1": True,
            "supportsVariableType": True,
        })
        self.debug("attach", {"justMyCode": False})
        code = """def local_viewers():
    text = "quote'\\\"\\\\\\n\\tКипр中文🙂" * 6000
    df = pd.DataFrame({'local_only': [text]})
    holder = {'table.with space': df}
    array = np.arange(12).reshape(3, 4)
    series = pd.Series([10, 20, 30])
    image = np.random.RandomState(42).randint(0, 256, (150, 150, 3)).astype(np.uint8)
    debugpy.breakpoint()
    return 42
local_viewers()
"""
        source = self.debug("dumpCell", {"code": code})["sourcePath"]
        running = self.client.execute(code, store_history=False)
        stopped = self.receive(
            self.client.get_iopub_msg, lambda message:
            message["msg_type"] == "debug_event"
            and message["content"].get("event") == "stopped",
        )
        thread_id = stopped["content"]["body"]["threadId"]
        frame = self.debug("stackTrace", {"threadId": thread_id})["stackFrames"][0]
        self.assertEqual("local_viewers", frame["name"])
        self.assertEqual(source, frame["source"]["path"])
        self.frame_id = frame["id"]
        self.assertEqual(
            debugpy.__version__, ast.literal_eval(self.evaluate("debugpy.__version__")),
        )
        self.assertEqual(
            str(Path(debugpy.__file__).resolve()),
            ast.literal_eval(self.evaluate("debugpy.__file__")),
        )

        queued = self.client.execute(
            "assert df.columns.tolist() == ['global_only']", store_history=False,
        )
        with self.assertRaises(queue.Empty):
            self.shell_reply(queued, timeout=0.25)

        table = self.viewer(
            "table", "holder['table.with space']", "SLICE_CSV", 0, 1, None,
        )
        self.assertGreater(len(table), 65536)
        decoded = pd.read_csv(
            io.StringIO(ast.literal_eval(table)), sep="~", index_col=0,
        )
        self.assertEqual(["local_only"], decoded.columns.tolist())
        self.assertEqual((1, 1), decoded.shape)
        self.assertEqual("quote'\"\\\n\tКипр中文🙂" * 6000, decoded.iloc[0, 0])
        self.assertEqual("(150, 150, 3)\tuint8", self.viewer("metadata", "image"))

        cases = (
            ("array", "ndarray", 3, 4, [str(i) for i in range(12)]),
            ("df", "DataFrame", 1, 1, ["quote'\"\\\n\tКипр中文🙂" * 6000]),
            ("series", "Series", 3, 1, ["10", "20", "30"]),
            ("series[1:]", "Series", 2, 1, ["20", "30"]),
        )
        for expression, type_name, rows, cols, expected_cells in cases:
            with self.subTest(expression=expression):
                evaluated = self.debug("evaluate", {
                    "expression": expression, "frameId": self.frame_id,
                    "context": "repl",
                })
                self.assertEqual(type_name, evaluated["type"])
                initial = ET.fromstring(self.viewer(
                    "array", expression, 0, 0, 0, 0, "%",
                ))
                metadata = initial.find("array").attrib
                self.assertEqual((str(rows), str(cols)), (
                    metadata["rows"], metadata["cols"],
                ))
                page = ET.fromstring(self.viewer(
                    "array", unquote(metadata["slice"]), 0, 0, rows, cols,
                    "%" + unquote(metadata["format"]),
                ))
                self.assertEqual(expected_cells, [
                    unquote(cell.attrib["value"]) for cell in page.findall("var")
                ])

        array = ET.fromstring(self.viewer("array", "array", 1, 1, 2, 2, "%d"))
        self.assertEqual({"rows": "2", "cols": "2"}, array.find("arraydata").attrib)
        self.assertEqual(
            ["5", "6", "9", "10"],
            [unquote(cell.attrib["value"]) for cell in array.findall("var")],
        )
        error = self.viewer(
            "table", "missing_name", "DF_INFO", None, None, None, success=False,
        )
        self.assertIn("NameError", error)

        image_id, size, dtype = self.viewer(
            "image", "image", "IMAGE_START_CHUNK_LOAD", None, None,
        ).split(";")
        self.assertEqual("uint8", dtype)
        self.assertGreater(int(size), 65536)
        offset, chunks = 0, []
        while offset != -1:
            data, next_offset = self.viewer(
                "image", "image", "IMAGE_CHUNK_LOAD", offset, image_id,
            ).split(";")
            chunks.append(base64.b64decode(data, validate=True))
            self.assertTrue(
                int(next_offset) == -1 or offset < int(next_offset) < int(size),
                "The image offset must advance or finish the snapshot",
            )
            offset = int(next_offset)
        self.assertGreater(len(chunks), 1)
        png = b"".join(chunks)
        self.assertEqual(int(size), len(png))
        expected = np.random.RandomState(42).randint(0, 256, (150, 150, 3))
        expected = expected.astype(np.uint8)
        with Image.open(io.BytesIO(png)) as image:
            np.testing.assert_array_equal(expected, np.asarray(image))
        self.assertEqual("False", self.evaluate(
            (
                "{!r} in __import__('pycharm_tables.images.pydevd_image_loader', "
                "fromlist=['IMAGE_DATA_STORAGE']).IMAGE_DATA_STORAGE"
            ).format(image_id),
        ))
        with self.assertRaises(queue.Empty):
            self.shell_reply(queued, timeout=0.25)
        self.debug("continue", {"threadId": thread_id})
        self.shell_reply(running)
        self.shell_reply(queued)
        self.debug("disconnect")


if __name__ == "__main__":
    unittest.main()
