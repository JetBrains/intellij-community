__author__ = 'Dmitry.Trofimov'

import unittest
import os

test_data_path = os.path.abspath(os.path.join(os.path.dirname(os.path.realpath(__file__)), '..', '..', 'testData', 'debug'))


class PyDevTestCase(unittest.TestCase):
    def testZipFileExits(self):
        from pydevd_file_utils import exists

        self.assertTrue(exists(test_data_path + '/zipped_lib.zip/zipped_module.py'))
        self.assertFalse(exists(test_data_path + '/zipped_lib.zip/zipped_module2.py'))
        self.assertFalse(exists(test_data_path + '/zipped_lib2.zip/zipped_module.py'))


    def testEggFileExits(self):
        from pydevd_file_utils import exists

        self.assertTrue(exists(test_data_path + '/pycharm-debug.egg/pydev/pydevd.py'))
        self.assertFalse(exists(test_data_path + '/pycharm-debug.egg/pydev/pydevd2.py'))
