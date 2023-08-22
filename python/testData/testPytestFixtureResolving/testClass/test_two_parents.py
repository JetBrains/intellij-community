from base_class_A import TestBaseClassA
from base_class_B import TestBaseClassB


class TestTwoParentClass(TestBaseClassB, TestBaseClassA):

    def test_simple(self, some_<caret>fixture):
        assert some_fixture == 'fixture from baseclass B'
