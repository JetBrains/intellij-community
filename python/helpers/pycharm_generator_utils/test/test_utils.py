import errno
import os

from pycharm_generator_utils.test import GeneratorTestCase
from pycharm_generator_utils.util_methods import copy_merging_packages, copy, delete, mkdir, copy_skeletons


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
        self.assertEqual(errno.EEXIST, cm.exception.errno)

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