# coding=utf-8
from __future__ import unicode_literals, print_function

import os
import shutil
import sys
import tempfile

from testing import HelpersTestCase, _helpers_root, python3_only

_pydevd_root = os.path.join(_helpers_root, 'debugpy', '_vendored', 'pydevd')


@python3_only
class PydevdCodeLinesCacheTest(HelpersTestCase):
    """
    PY-92233: a `setBreakpoints` request validates each breakpoint of a file against the code lines of the file,
    and reading those lines compiles the whole file. The lines are now kept per file until the file changes.
    """

    @classmethod
    def setUpClass(cls):
        super(PydevdCodeLinesCacheTest, cls).setUpClass()
        sys.path.insert(0, _pydevd_root)
        from _pydevd_bundle import pydevd_api
        cls.pydevd_api = pydevd_api

    @classmethod
    def tearDownClass(cls):
        sys.path.remove(_pydevd_root)
        super(PydevdCodeLinesCacheTest, cls).tearDownClass()

    def setUp(self):
        super(PydevdCodeLinesCacheTest, self).setUp()
        self.directory = tempfile.mkdtemp()
        self.path = os.path.join(self.directory, 'module.py')
        self.compilations = 0
        self.original_get_code_lines = self.pydevd_api._get_code_lines
        self.pydevd_api._get_code_lines = self._counting_get_code_lines
        self.pydevd_api._sorted_code_lines_cache.clear()

    def tearDown(self):
        self.pydevd_api._get_code_lines = self.original_get_code_lines
        self.pydevd_api._sorted_code_lines_cache.clear()
        shutil.rmtree(self.directory)
        super(PydevdCodeLinesCacheTest, self).tearDown()

    def _counting_get_code_lines(self, path_or_code):
        # A call with a path compiles the file; the reader then calls itself with the code objects of the file.
        if isinstance(path_or_code, str):
            self.compilations += 1
        return self.original_get_code_lines(path_or_code)

    def _write(self, source):
        with open(self.path, 'w') as f:
            f.write(source)

    def _lines_of_the_file(self):
        # What `add_breakpoint` read before the cache. The module code object adds a line 0 on some Python versions.
        compilations = self.compilations
        lines = sorted(self.original_get_code_lines(self.path))
        self.compilations = compilations
        return lines

    def test_the_file_is_compiled_once_for_all_its_breakpoints(self):
        self._write('x = 1\ny = 2\n')
        expected = self._lines_of_the_file()
        first = self.pydevd_api._get_sorted_code_lines(self.path)
        second = self.pydevd_api._get_sorted_code_lines(self.path)
        self.assertEqual(expected, first)
        self.assertEqual(expected, second)
        self.assertEqual(1, self.compilations)

    def test_a_changed_file_is_compiled_again(self):
        self._write('x = 1\n')
        self.assertEqual(self._lines_of_the_file(), self.pydevd_api._get_sorted_code_lines(self.path))
        self._write('x = 1\n\ny = 2\nz = 3\n')
        lines = self.pydevd_api._get_sorted_code_lines(self.path)
        self.assertEqual(self._lines_of_the_file(), lines)
        self.assertIn(4, lines)
        self.assertEqual(2, self.compilations)

    def test_a_missing_file_fails_as_before(self):
        # `add_breakpoint` ignores the failure and keeps the requested line, as it did without the cache.
        with self.assertRaises(OSError):
            self.pydevd_api._get_sorted_code_lines(os.path.join(self.directory, 'missing.py'))
