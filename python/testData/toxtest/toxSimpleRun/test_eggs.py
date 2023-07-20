import unittest




class MyTest(unittest.TestCase):
    def test_test(self):
        import sys; assert sys.version_info[:2] == (3,7)

    def test_dtest(self):
       pass
