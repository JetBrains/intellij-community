import pytest


@pytest.fixture()
def exit():
    pytest.exit("reason")


def test_(exit):
    assert True