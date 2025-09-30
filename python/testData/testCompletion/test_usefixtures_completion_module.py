import pytest


@pytest.fixture
def my_fixture():
    return True


# Module-level usefixtures via pytestmark
pytestmark = pytest.mark.usefixtures("<caret>")

def test_abc():
    ...
