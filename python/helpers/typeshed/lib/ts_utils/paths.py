from pathlib import Path
from typing import Final

# TODO: Use base path relative to this file. Currently, ts_utils gets
# installed into the user's virtual env, so we can't determine the path
# to typeshed. Installing ts_utils editable would solve that, see
# https://github.com/python/typeshed/pull/12806.
TS_BASE_PATH: Final = Path()
STDLIB_PATH: Final = TS_BASE_PATH / "stdlib"
STUBS_PATH: Final = TS_BASE_PATH / "stubs"

PYPROJECT_PATH: Final = TS_BASE_PATH / "pyproject.toml"
REQUIREMENTS_PATH: Final = TS_BASE_PATH / "requirements-tests.txt"
GITIGNORE_PATH: Final = TS_BASE_PATH / ".gitignore"
PYRIGHT_CONFIG: Final = TS_BASE_PATH / "pyrightconfig.stricter.json"

TESTS_DIR: Final = "@tests"
TEST_CASES_DIR: Final = "test_cases"


def distribution_path(distribution_name: str) -> Path:
    """Return the path to the directory of a third-party distribution."""
    return STUBS_PATH / distribution_name


def tests_path(distribution_name: str) -> Path:
    if distribution_name == "stdlib":
        return STDLIB_PATH / TESTS_DIR
    else:
        return STUBS_PATH / distribution_name / TESTS_DIR


def test_cases_path(distribution_name: str) -> Path:
    return tests_path(distribution_name) / TEST_CASES_DIR


def allowlists_path(distribution_name: str) -> Path:
    if distribution_name == "stdlib":
        return tests_path("stdlib") / "stubtest_allowlists"
    else:
        return tests_path(distribution_name)
