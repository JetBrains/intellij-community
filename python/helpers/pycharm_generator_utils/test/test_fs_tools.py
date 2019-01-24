import errno
import os
import shutil
import tempfile
from contextlib import contextmanager
from unittest import TestCase

from pycharm_generator_utils.util_methods import copy_merging_packages, delete, mkdir, copy_skeletons, copy

_test_dir = os.path.dirname(__file__)
_test_data_root_dir = os.path.join(_test_dir, 'data')


class GeneratorTestCase(TestCase):
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
        return os.path.join(_test_data_root_dir,
                            self.test_class_name, self.test_name)

    def assertDirsEqual(self, actual_dir, expected_dir):
        actual_dir_children = sorted(os.listdir(actual_dir))
        expected_dir_children = sorted(os.listdir(expected_dir))
        self.assertEquals(expected_dir_children, actual_dir_children)
        for actual_child, expected_child in zip(actual_dir_children, expected_dir_children):
            actual_child = os.path.join(actual_dir, actual_child)
            expected_child = os.path.join(expected_dir, expected_child)
            if os.path.isdir(actual_child) and os.path.isdir(expected_child):
                self.assertDirsEqual(actual_child, expected_child)
            elif os.path.isfile(actual_child) and os.path.isfile(expected_child):
                with open(actual_child) as f:
                    actual_child_content = f.read()
                with open(expected_child) as f:
                    expected_child_content = f.read()
                self.assertEquals(expected_child_content, actual_child_content,
                                  'Different content at %r' % actual_child)
            else:
                raise AssertionError('%r != %r' % (actual_child, expected_child))

    @contextmanager
    def comparing_dirs(self, subdir=''):
        before_dir = os.path.join(self.test_data_dir, subdir, 'before')
        after_dir = os.path.join(self.test_data_dir, subdir, 'after')
        for child_name in os.listdir(before_dir):
            child_path = os.path.join(before_dir, child_name)
            child_dst_path = os.path.join(self.temp_dir, child_name)
            if os.path.isfile(child_path):
                shutil.copy2(child_path, child_dst_path)
            elif os.path.isdir(child_path):
                shutil.copytree(child_path, child_dst_path)
        yield
        self.assertDirsEqual(self.temp_dir, after_dir)


class FileSystemUtilTest(GeneratorTestCase):

    def check_copy_merging_packages(self):
        with self.comparing_dirs('dst'):
            src_dir = os.path.join(self.test_data_dir, 'src')
            dst_dir = self.temp_dir
            copy_merging_packages(src_dir, dst_dir)

    def check_copy(self, src, dst, **kwargs):
        with self.comparing_dirs():
            copy(os.path.join(self.temp_dir, src),
                 os.path.join(self.temp_dir, dst), **kwargs)

    def check_delete(self, rel_name):
        with self.comparing_dirs():
            delete(os.path.join(self.temp_dir, rel_name))

    def check_mkdir(self, rel_name):
        with self.comparing_dirs():
            mkdir(os.path.join(self.temp_dir, rel_name))

    def check_copy_skeletons(self):
        with self.comparing_dirs('dst'):
            src_dir = os.path.join(self.test_data_dir, 'src')
            dst_dir = self.temp_dir
            copy_skeletons(src_dir, dst_dir)

    def test_copy_merging_packages_simple(self):
        self.check_copy_merging_packages()

    def test_mkdir_with_existing_dir(self):
        self.check_mkdir('existing')

    def test_mkdir_with_exiting_file(self):
        with self.assertRaises(OSError) as cm:
            self.check_mkdir('existing')
        self.assertEquals(errno.EEXIST, cm.exception.errno)

    def test_delete_with_absent_file(self):
        self.check_delete('absent')

    def test_copy_file_creating_dir_structure(self):
        self.check_copy('baz.txt', os.path.join('foo', 'bar', 'baz.txt'))

    def test_copy_skeleton_module_replaced_with_package(self):
        self.check_copy_skeletons()

    def test_copy_skeleton_package_replaced_with_module(self):
        self.check_copy_skeletons()

    def test_copy_skeleton_module_replaced(self):
        self.check_copy_skeletons()

    def test_copy_skeleton_package_replaced(self):
        self.check_copy_skeletons()

    def test_copy_several_skeletons(self):
        self.check_copy_skeletons()
