import os
import tempfile
import unittest


class SomeTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.renamed = tempfile.mkstemp()

    @classmethod
    def tearDownClass(cls):
        os.remove(cls.renamed[0])