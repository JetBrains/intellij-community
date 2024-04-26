import os
import tempfile
import unittest


class SomeTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._<caret>old = tempfile.mkstemp()

    @classmethod
    def tearDownClass(cls):
        os.remove(cls._old[0])