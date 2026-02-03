import pytest


@pytest.fixture
def my_fixture():
    return True


@pytest.mark.usefixtures("<caret>")
class TestSuite:
    def test_abc(self):
        ...
