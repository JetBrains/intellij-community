import pytest


class TestBaseClassA:

    @pytest.fixture
    def some_fixture(self):
        return 'fixture from baseclass A'

    def test_simple(self, some_fixture):
        assert some_fixture == 'fixture from baseclass A'
