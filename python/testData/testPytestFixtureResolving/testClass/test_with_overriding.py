import pytest

from base_class_A import TestBaseClassA


class TestNewClass(TestBaseClassA):

    @pytest.fixture
    def some_fixture(self):
        return 'fixture from TestNewClass'

    def test_simple(self, some_<caret>fixture):
        assert some_fixture == 'fixture from TestNewClass'
