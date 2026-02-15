import pytest


class A:
    ...


@pytest.fixture
def instance():
    return A()


def test(inst<caret>ance):
    assert instance
