import pytest


@pytest.fixture
def foo():
    return 1


def test_():
    assert <warning descr="Fixture 'foo' is not requested by test functions or @pytest.mark.usefixtures marker">foo</warning> == 1