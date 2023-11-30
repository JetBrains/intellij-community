import pytest

class TestClass:
    def test(self, some_<caret>fixture):
        assert some_fixture == 'fixture from conftest'
