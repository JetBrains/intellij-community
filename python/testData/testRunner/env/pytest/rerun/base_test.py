from unittest import TestCase
class BaseTest(TestCase):
    __test__ = False
    def test_something(self):
        s = self.system_under_test()
        assert s == 'ok'
    def system_under_test(self):
        raise NotImplementedError