import unittest


class TestSimple(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        raise unittest.SkipTest("Skip whole Case")

    def test_true(self):
        self.assertTrue(True)

    def test_false(self):
        self.assertTrue(False, msg="Is not True")

    def test_skip(self):
        raise unittest.SkipTest("Skip this test")


class TestSubSimple(TestSimple):

    def test_subclass(self):
        self.assertTrue(True)