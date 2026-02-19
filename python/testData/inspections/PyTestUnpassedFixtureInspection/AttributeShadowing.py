import pytest


class Unrelated:
    ...


@pytest.fixture
def fixture():
    return True


def test_():
    assert Unrelated().fixture  # no inspection warning expected
