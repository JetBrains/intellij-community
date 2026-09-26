from .base_test import BaseTest
class TestFoo(BaseTest):
    __test__ = True
    def system_under_test(self):
        return 'foo'
class TestBar(BaseTest):
    __test__ = True
    def system_under_test(self):
        return 'bar'