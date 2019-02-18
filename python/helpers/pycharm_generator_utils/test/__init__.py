import os
import shutil
import tempfile
from contextlib import contextmanager
from unittest import TestCase

_test_dir = os.path.dirname(__file__)
_test_data_root_dir = os.path.join(_test_dir, 'data')
_override_test_data = False


class GeneratorTestCase(TestCase):
    longMessage = True

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

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
        self.assertEqual(expected_dir_children, actual_dir_children)
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