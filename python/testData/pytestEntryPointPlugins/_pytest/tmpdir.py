from pathlib import Path

from _pytest.fixtures import fixture


@fixture
def tmp_path() -> Path:
    return Path()
