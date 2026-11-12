import pytest


def pytest_configure(config) -> None:
    config.addinivalue_line("markers", "unregistered_marker: A marker of no registered plugin.")


@pytest.fixture()
def unregistered() -> int:
    return 1
