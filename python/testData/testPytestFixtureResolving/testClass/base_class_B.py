import pytest


class TestBaseClassB:

    @pytest.fixture
    def some_fixture(self):
        return 'fixture from baseclass B'

    def test_simple(self, some_fixture):
        assert some_fixture == 'fixture from baseclass B'
