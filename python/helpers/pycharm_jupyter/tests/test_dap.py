# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import ast
import base64
import io
import unittest
import xml.etree.ElementTree as ET
from urllib.parse import unquote

import debugpy._vendored.force_pydevd
import numpy as np
import pandas as pd
from PIL import Image

from pycharm_jupyter import dap
from pycharm_tables.images.pydevd_image_loader import IMAGE_DATA_STORAGE


class DapDataViewerTest(unittest.TestCase):
    def test_array_compatibility_does_not_change_the_shared_serializer(self):
        from pycharm_tables import pydevd_tabular_to_xml
        from _pydevd_bundle import pydevd_xml
        original_writer = pydevd_tabular_to_xml.var_to_xml
        self.assertIs(pydevd_xml.var_to_xml, original_writer)
        result = self.decode(dap.array(
            "value", 0, 0, 1, 1, "%.2f", {}, {"value": np.array([1.25])},
        ))
        self.assertEqual(["1.25"], self.cells(ET.fromstring(result)))
        self.assertIs(original_writer, pydevd_tabular_to_xml.var_to_xml)
        self.assertIs(original_writer, pydevd_xml.var_to_xml)

    def decode(self, response, success=True):
        prefix = "PYCHARM_DATAVIEW_1:{}:".format("OK" if success else "ERROR")
        self.assertTrue(response.startswith(prefix), response[:200])
        return base64.b64decode(response[len(prefix):], validate=True).decode("utf-8")

    def test_table_uses_locals_and_preserves_large_unicode_cells(self):
        text = "quote'\"\\\n\tКипр中文🙂" * 6000
        local = pd.DataFrame({"text": [text]})
        result = self.decode(dap.table(
            "holder['table.with space']", "SLICE_CSV", 0, 1, None,
            {"holder": {"table.with space": pd.DataFrame({"text": ["global"]})}},
            {"holder": {"table.with space": local}},
        ))
        decoded = pd.read_csv(
            io.StringIO(ast.literal_eval(result)), sep="~", index_col=0,
        )
        self.assertEqual((1, 1), decoded.shape)
        self.assertEqual(text, decoded.iloc[0, 0])

    def test_metadata_uses_the_selected_value(self):
        value = np.zeros((3, 4, 3), dtype=np.uint8)
        result = self.decode(dap.metadata("value", {"value": [1]}, {"value": value}))
        self.assertEqual("(3, 4, 3)\tuint8", result)

    def test_array_applies_offsets_and_format(self):
        value = np.arange(20, dtype=float).reshape(4, 5) / 4
        result = self.decode(dap.array(
            "value", 1, 2, 2, 2, "%.2f", {}, {"value": value},
        ))
        root = ET.fromstring(result)
        self.assertEqual({"rows": "2", "cols": "2"}, root.find("arraydata").attrib)
        self.assertEqual(
            ["1.75", "2.00", "3.00", "3.25"],
            [unquote(item.attrib["value"]) for item in root.findall("var")],
        )

    def test_array_preserves_text_and_non_finite_values(self):
        cases = [
            (np.array([["<&\"'\n🙂", "tail"]]), "%s", ["<&\"'\n🙂", "tail"]),
            (np.array([[float("nan"), float("inf")]]), "%.2f", ["nan", "inf"]),
            (np.array([[True, False]]), "%s", ["True", "False"]),
        ]
        for value, format, expected in cases:
            with self.subTest(dtype=value.dtype):
                result = self.decode(dap.array(
                    "value", 0, 0, 1, 2, format, {}, {"value": value},
                ))
                root = ET.fromstring(result)
                self.assertEqual(expected, self.cells(root))

    def test_dataframe_and_series_xml_preserve_headers_and_cells(self):
        for value in (pd.DataFrame({"amount": [1.25, 2.5]}, index=["first", "last"]),
                      pd.Series([1.25, 2.5], index=["first", "last"])):
            with self.subTest(type=type(value).__name__):
                result = self.decode(dap.array(
                    "value", 0, 0, 2, 1, "%.2f", {}, {"value": value},
                ))
                root = ET.fromstring(result)
                self.assertEqual(["1.25", "2.50"], self.cells(root))
                headers = root.findall("headerdata/rowheader")
                self.assertEqual(
                    ["first", "last"],
                    [unquote(item.attrib["label"]) for item in headers],
                )

    @staticmethod
    def cells(root):
        return [unquote(item.attrib["value"]) for item in root.findall("var")]

    def test_image_chunks_preserve_pixels_and_release_the_snapshot(self):
        pixels = np.random.RandomState(42).randint(0, 256, (150, 150, 3))
        pixels = pixels.astype(np.uint8)
        namespace = {"image": pixels}
        start = self.decode(dap.image(
            "image", "IMAGE_START_CHUNK_LOAD", None, None, {}, namespace,
        ))
        image_id, size, dtype = start.split(";")
        try:
            namespace.clear()
            self.assertEqual("uint8", dtype)
            self.assertGreater(int(size), 65536)
            chunks = []
            offset = 0
            while offset != -1:
                result = self.decode(dap.image(
                    "image", "IMAGE_CHUNK_LOAD", offset, image_id, {}, namespace,
                ))
                data, next_offset = result.split(";")
                chunks.append(base64.b64decode(data, validate=True))
                self.assertTrue(
                    int(next_offset) == -1 or offset < int(next_offset) < int(size),
                    "The image offset must advance or finish the snapshot",
                )
                offset = int(next_offset)
            self.assertGreater(len(chunks), 1)
            png = b"".join(chunks)
            self.assertEqual(int(size), len(png))
            with Image.open(io.BytesIO(png)) as image:
                np.testing.assert_array_equal(pixels, np.asarray(image))
            self.assertNotIn(image_id, IMAGE_DATA_STORAGE)
        finally:
            IMAGE_DATA_STORAGE.pop(image_id, None)

    def test_evaluation_and_provider_errors_are_explicit(self):
        cases = [
            ("missing_name", "NameError"),
            ("1 / 0", "ZeroDivisionError"),
            ("42", "RuntimeError"),
        ]
        for expression, error in cases:
            with self.subTest(expression=expression):
                result = self.decode(
                    dap.table(expression, "DF_INFO", None, None, None, {}, {}),
                    success=False,
                )
                self.assertIn(error, result)

    def test_invalid_image_id_is_an_error(self):
        result = self.decode(dap.image(
            "image", "IMAGE_CHUNK_LOAD", 0, "missing", {}, {"image": np.zeros((2, 2))},
        ), success=False)
        self.assertIn("No image data found", result)


if __name__ == "__main__":
    unittest.main()
