import pytest


@pytest.fixture
def boom():
    yield
    raise RuntimeError("Boom!")
