from base_class_A import TestBaseClassA


class TestOtherClass(TestBaseClassA):

    def test_simple(self, some_<caret>fixture):
        assert some_fixture == 'fixture from baseclass A'
