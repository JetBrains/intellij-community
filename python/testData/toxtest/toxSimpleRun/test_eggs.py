import unittest




class MyTest(unittest.TestCase):
    def test_test(self):
        import sys; assert sys.version_info[:2] == (3,9)

    def test_dtest(self):
       pass
