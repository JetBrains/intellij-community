import logging
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import contextmanager
from io import open

from pycharm_generator_utils.constants import ENV_TEST_MODE_FLAG

_test_dir = os.path.dirname(__file__)
_test_data_root_dir = os.path.join(_test_dir, 'data')
_override_test_data = False


class GeneratorTestCase(unittest.TestCase):
    longMessage = True

    @classmethod
    def setUpClass(cls):
        super(GeneratorTestCase, cls).setUpClass()
        # Logger cannot be initialized beforehand (say, on top-level), because,
        # otherwise, it won't take into account buffered sys.stderr needed by
        # teamcity-messages
        cls.log = logging.getLogger(cls.__name__)
        handler = logging.StreamHandler(sys.stderr)
        handler.setFormatter(logging.Formatter(fmt='%(levelname)s:%(name)s:%(message)s'))
        cls.log.addHandler(handler)
        cls.log.setLevel(logging.DEBUG)

        os.environ[ENV_TEST_MODE_FLAG] = 'True'

    @classmethod
    def tearDownClass(cls):
        os.environ.pop(ENV_TEST_MODE_FLAG)

        delattr(cls, 'log')
        super(GeneratorTestCase, cls).tearDownClass()

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix='{}_{}__'.format(self.test_class_name, self.test_name))

    def tearDown(self):
        if not self._test_has_failed():
            shutil.rmtree(self.temp_dir)

    def _test_has_failed(self):
        try:
            result = self._resultForDoCleanups  # type: unittest.TestResult
            return result.failures or result.errors
        except AttributeError:
            pass

        try:
            return any(error for (method, error) in self._outcome.errors)
        except AttributeError:
            pass

        return False

    @property
    def test_name(self):
        return self._testMethodName[len('test_'):]

    @property
    def test_class_name(self):
        return self.__class__.__name__[:-len('Test')]

    @property
    def test_data_dir(self):
        return os.path.join(self.class_test_data_dir, self.test_name)

    @property
    def class_test_data_dir(self):
        return os.path.join(_test_data_root_dir, self.test_class_name)

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
            elif os.path.isfile(actual_child) and os.path.isfile(
                    expected_child):
                with open(actual_child) as f:
                    actual_child_content = f.read()
                with open(expected_child) as f:
                    expected_child_content = f.read()
                try:
                    self.assertMultiLineEqual(expected_child_content, actual_child_content,
                                              'Different content at {!r}'.format(actual_child))
                except AssertionError:
                    if _override_test_data:
                        with open(expected_child, 'w') as f:
                            f.write(actual_child_content)
                    raise
            else:
                raise AssertionError(
                    '%r != %r' % (actual_child, expected_child))

    def assertNonEmptyFile(self, path):
        with open(path) as f:
            content = f.read()
            self.assertTrue(content and not content.isspace(),
                            "File {!r} is empty or contains only whitespaces".format(path))

    @contextmanager
    def comparing_dirs(self, subdir='', tmp_subdir=''):
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
