import pytest

from . import plugin as plugin


@pytest.fixture()
def package_widget() -> int:
    return 1


def main() -> None:
    pass
