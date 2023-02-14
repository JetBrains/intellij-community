import unittest

class TestStringMethods(unittest.TestCase):

    def test_is_ok(self):
        str = get_response()
        if isinstance(str, int):
            self.fail()
            print("Not a string!")
        self.assert_(str, "OK")