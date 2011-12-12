__author__ = 'Dmitry.Trofimov'

import unittest

class PyDevTestCase(unittest.TestCase):
    def testZipFileExits(self):
        from pydevd_file_utils import exists
        self.assertTrue(exists('../../testData/debug/zipped_lib.zip/zipped_module.py'))
        self.assertFalse(exists('../../testData/debug/zipped_lib.zip/zipped_module2.py'))
        self.assertFalse(exists('../../testData/debug/zipped_lib2.zip/zipped_module.py'))


    def testEggFileExits(self):
        from pydevd_file_utils import exists
        self.assertTrue(exists('../../testData/debug/pycharm-debug.egg/pydev/pydevd.py'))
        self.assertFalse(exists('../../testData/debug/pycharm-debug.egg/pydev/pydevd2.py'))
