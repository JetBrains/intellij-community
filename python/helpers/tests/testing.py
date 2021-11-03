from __future__ import unicode_literals

import logging
import os
import shutil
import sys
import tempfile
import textwrap
import unittest
import zipfile
from contextlib import contextmanager

import six

if six.PY2:
    from io import open

_test_root = os.path.dirname(os.path.abspath(__file__))
_test_data_root = os.path.join(_test_root, 'data')
_override_test_data = False


class HelpersTestCase(unittest.TestCase):
    longMessage = True
    maxDiff = None

    @classmethod
    def setUpClass(cls):
        super(HelpersTestCase, cls).setUpClass()
        # Logger cannot be initialized beforehand (say, on top-level), because,
        # otherwise, it won't take into account buffered sys.stderr needed by
        # teamcity-messages
        cls.log = logging.getLogger(cls.__name__)
        handler = logging.StreamHandler(sys.stderr)
        handler.setFormatter(logging.Formatter(fmt='%(levelname)s:%(name)s:%(message)s'))
        cls.log.addHandler(handler)
        cls.log.setLevel(logging.WARN)

    @classmethod
    def tearDownClass(cls):
        delattr(cls, 'log')
        super(HelpersTestCase, cls).tearDownClass()

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(
            prefix='{}_{}__'.format(self.test_class_name, self.test_name))

    def tearDown(self):
        if self._test_has_failed():
            self.tearDownForFailedTest()
        else:
            self.tearDownForSuccessfulTest()

    def tearDownForSuccessfulTest(self):
        shutil.rmtree(self.temp_dir)

    def tearDownForFailedTest(self):
        pass

    def _test_has_failed(self):
        try:
            return any(error for _, error in self._outcome.errors)
        except AttributeError:
            # Python 2 fallback
            class_result = self._resultForDoCleanups  # type: unittest.TestResult
            problems = class_result.failures + class_result.errors
            return any(test is self for test, _ in problems)

    @property
    def test_name(self):
        return self._testMethodName[len('test_'):]

    @property
    def test_class_name(self):
        return self.__class__.__name__[:-len('Test')]

    @property
    def test_data_dir(self):
        test_method = getattr(self, self._testMethodName)
        dir_name = getattr(test_method, '_test_data_dir', self.test_name)
        return os.path.join(self.class_test_data_dir, dir_name)

    @property
    def class_test_data_dir(self):
        return os.path.join(self.test_data_root, self.test_class_name)

    def resolve_in_test_data(self, rel_path):
        return os.path.join(self.test_data_dir, rel_path)

    def resolve_in_temp_dir(self, rel_path):
        return os.path.join(self.temp_dir, rel_path)

    @property
    def test_data_root(self):
        return _test_data_root

    def assertDirsEqual(self, actual_dir, expected_dir):
        actual_dir_children = sorted(os.listdir(actual_dir))
        expected_dir_children = sorted(os.listdir(expected_dir))
        self.assertEqual(expected_dir_children, actual_dir_children,
                         'Children differ at {!r}'.format(actual_dir))
        for actual_child, expected_child in zip(actual_dir_children,
                                                expected_dir_children):
            actual_child = os.path.join(actual_dir, actual_child)
            expected_child = os.path.join(expected_dir, expected_child)
            if os.path.isdir(actual_child) and os.path.isdir(expected_child):
                self.assertDirsEqual(actual_child, expected_child)
            elif zipfile.is_zipfile(actual_child) and zipfile.is_zipfile(expected_child):
                self.assertZipFilesEqual(actual_child, expected_child)
            elif os.path.isfile(actual_child) and os.path.isfile(expected_child):
                try:
                    try:
                        with open(actual_child) as f:
                            actual_text = f.read()
                        with open(expected_child) as f:
                            expected_text = f.read()
                    except UnicodeDecodeError:
                        self.assertBinaryFilesEqual(actual_child, expected_child)
                    else:
                        self.assertMultiLineEqual(expected_text, actual_text,
                                                  'Different content at {!r}'.format(actual_child))
                except AssertionError:
                    if _override_test_data:
                        with open(expected_child, 'w') as f:
                            f.write(actual_text)
                    raise
            else:
                raise AssertionError('%r != %r' % (actual_child, expected_child))

    def assertNonEmptyFile(self, path):
        with open(path) as f:
            content = f.read()
            self.assertTrue(content and not content.isspace(),
                            "File {!r} is empty or contains only whitespaces".format(path))

    @contextmanager
    def comparing_dirs(self, subdir='', tmp_subdir=''):
        self.assertTrue(os.path.exists(self.test_data_dir),
                        "Test data directory {!r} doesn't exist".format(self.test_data_dir))
        before_dir = os.path.join(self.test_data_dir, subdir, 'before')
        after_dir = os.path.join(self.test_data_dir, subdir, 'after')
        dst_dir = os.path.join(self.temp_dir, tmp_subdir)
        if os.path.exists(before_dir):
            for child_name in os.listdir(before_dir):
                child_path = os.path.join(before_dir, child_name)
                child_dst_path = os.path.join(dst_dir, child_name)
                if os.path.isfile(child_path):
                    shutil.copy2(child_path, child_dst_path)
                elif os.path.isdir(child_path):
                    shutil.copytree(child_path, child_dst_path)
        yield dst_dir
        self.assertDirsEqual(dst_dir, after_dir)

    def assertContainsInRelativeOrder(self, expected, actual):
        actual_list = list(actual)
        prev_index, prev_item = -1, None
        for item in expected:
            try:
                prev_index = actual_list.index(item, prev_index + 1)
                prev_item = item
            except ValueError:
                try:
                    index = actual_list.index(item)
                    if index <= prev_index:
                        raise AssertionError(
                            'Item {!r} is expected after {!r} in {!r}'.format(item, prev_item,
                                                                              actual_list))
                except ValueError:
                    raise AssertionError('Item {!r} not found in {!r}'.format(item, actual_list))

    def assertDirLayoutEquals(self, dir_path, expected_layout):
        def format_dir(dir_path, indent=''):
            for child_name in sorted(os.listdir(dir_path)):
                child_path = os.path.join(dir_path, child_name)
                if os.path.isdir(child_path):
                    yield indent + child_name + '/'
                    for line in format_dir(child_path, indent + '    '):
                        yield line
                else:
                    yield indent + child_name

        formatted_dir_tree = '\n'.join(format_dir(dir_path))
        expected = textwrap.dedent(expected_layout).strip() + '\n'
        actual = formatted_dir_tree.strip() + '\n'
        self.assertMultiLineEqual(expected, actual)

    def assertBinaryFilesEqual(self, path1, path2):
        self.assertEqual(os.stat(path1).st_size, os.stat(path2).st_size,
                         'Sizes of {!r} and {!r} differ'.format(path1, path2))

        with open(path1, 'rb') as f1:
            content1 = f1.read()
        with open(path2, 'rb') as f2:
            content2 = f2.read()
        self.assertEqual(content1, content2,
                         'Binary content of {!r} and {!r} differs'.format(path1, path2))

    def assertZipFilesEqual(self, path1, path2):
        entries = self.read_zip_entries(path1)
        self.assertEqual(entries, self.read_zip_entries(path2),
                         'Entries of ZIP files {!r} and {!r} differ'.format(path1, path2))
        with zipfile.ZipFile(path1) as zf1, zipfile.ZipFile(path2) as zf2:
            for entry in entries:
                content1 = zf1.open(entry).read()
                content2 = zf2.open(entry).read()
                self.assertEqual(content1, content2,
                                 'ZIP files {!r} and {!r} differ at entry {!r}'
                                 .format(path1, path2, entry))

    def read_zip_entries(self, path):
        with zipfile.ZipFile(path, 'r') as zf:
            return sorted(zf.namelist())
