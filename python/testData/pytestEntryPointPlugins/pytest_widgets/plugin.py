import pytest


class Widget:
    pass


@pytest.fixture(name="widget")
def widget_fixture() -> Widget:
    return Widget()


@pytest.fixture()
def monkeypatch() -> Widget:
    return Widget()
