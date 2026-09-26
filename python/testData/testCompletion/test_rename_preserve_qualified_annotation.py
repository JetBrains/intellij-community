import types
import pytest


@pytest.fixture()
def client<caret>():
    return types.SimpleNamespace(get=lambda: None)


def test(client: types.SimpleNamespace):
    assert True
