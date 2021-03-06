import pytest


class App:
    def __init__(self):
        d = []
        self.result = d[100500]


@pytest.fixture(scope="module")
def app():
    return App()


def test_app(app):
    assert True
