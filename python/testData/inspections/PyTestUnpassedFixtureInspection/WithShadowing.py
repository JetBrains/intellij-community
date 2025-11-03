import pytest
from contextlib import contextmanager


class C:
    def bla(self):
        ...


@contextmanager
def create():
    yield C()


@pytest.fixture
def client():
    pass


def test_():
    with create() as client:
        assert client.bla() == 1  # no inspection warning expected