"""Utilities that are imported by multiple scripts in the tests directory."""

from __future__ import annotations

import re
import sys
from collections.abc import Iterable, Mapping
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, Final, NamedTuple, Tuple
from typing_extensions import TypeAlias

import pathspec
from packaging.requirements import Requirement

try:
    from termcolor import colored as colored  # pyright: ignore[reportAssignmentType]
except ImportError:

    def colored(text: str, color: str | None = None, **kwargs: Any) -> str:  # type: ignore[misc]
        return text


PYTHON_VERSION: Final = f"{sys.version_info.major}.{sys.version_info.minor}"

STDLIB_PATH = Path("stdlib")
STUBS_PATH = Path("stubs")


# A backport of functools.cache for Python <3.9
# This module is imported by mypy_test.py, which needs to run on 3.8 in CI
cache = lru_cache(None)


def strip_comments(text: str) -> str:
    return text.split("#")[0].strip()


# ====================================================================
# Printing utilities
# ====================================================================


def print_command(cmd: str | Iterable[str]) -> None:
    if not isinstance(cmd, str):
        cmd = " ".join(cmd)
    print(colored(f"Running: {cmd}", "blue"))


def print_error(error: str, end: str = "\n", fix_path: tuple[str, str] = ("", "")) -> None:
    error_split = error.split("\n")
    old, new = fix_path
    for line in error_split[:-1]:
        print(colored(line.replace(old, new), "red"))
    print(colored(error_split[-1], "red"), end=end)


def print_success_msg() -> None:
    print(colored("success", "green"))


def print_divider() -> None:
    """Print a row of * symbols across the screen.

    This can be useful to divide terminal output into separate sections.
    """
    print()
    print("*" * 70)
    print()


# ====================================================================
# Dynamic venv creation
# ====================================================================


@cache
def venv_python(venv_dir: Path) -> Path:
    if sys.platform == "win32":
        return venv_dir / "Scripts" / "python.exe"
    return venv_dir / "bin" / "python"


# ====================================================================
# Parsing the requirements file
# ====================================================================


REQS_FILE: Final = "requirements-tests.txt"


@cache
def parse_requirements() -> Mapping[str, Requirement]:
    """Return a dictionary of requirements from the requirements file."""

    with open(REQS_FILE, encoding="UTF-8") as requirements_file:
        stripped_lines = map(strip_comments, requirements_file)
        requirements = map(Requirement, filter(None, stripped_lines))
        return {requirement.name: requirement for requirement in requirements}


def get_mypy_req() -> str:
    return str(parse_requirements()["mypy"])


# ====================================================================
# Parsing the stdlib/VERSIONS file
# ====================================================================

VersionTuple: TypeAlias = Tuple[int, int]
SupportedVersionsDict: TypeAlias = Dict[str, Tuple[VersionTuple, VersionTuple]]

VERSIONS_PATH = STDLIB_PATH / "VERSIONS"
VERSION_LINE_RE = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_.]*): ([23]\.\d{1,2})-([23]\.\d{1,2})?$")
VERSION_RE = re.compile(r"^([23])\.(\d+)$")


def parse_stdlib_versions_file() -> SupportedVersionsDict:
    result: dict[str, tuple[VersionTuple, VersionTuple]] = {}
    with VERSIONS_PATH.open(encoding="UTF-8") as f:
        for line in f:
            line = strip_comments(line)
            if line == "":
                continue
            m = VERSION_LINE_RE.match(line)
            assert m, f"invalid VERSIONS line: {line}"
            mod: str = m.group(1)
            assert mod not in result, f"Duplicate module {mod} in VERSIONS"
            min_version = _parse_version(m.group(2))
            max_version = _parse_version(m.group(3)) if m.group(3) else (99, 99)
            result[mod] = min_version, max_version
    return result


def _parse_version(v_str: str) -> tuple[int, int]:
    m = VERSION_RE.match(v_str)
    assert m, f"invalid version: {v_str}"
    return int(m.group(1)), int(m.group(2))


# ====================================================================
# Test Directories
# ====================================================================


TESTS_DIR: Final = "@tests"
TEST_CASES_DIR: Final = "test_cases"


class DistributionTests(NamedTuple):
    name: str
    test_cases_path: Path

    @property
    def is_stdlib(self) -> bool:
        return self.name == "stdlib"


def distribution_info(distribution_name: str) -> DistributionTests:
    if distribution_name == "stdlib":
        return DistributionTests("stdlib", test_cases_path("stdlib"))
    test_path = test_cases_path(distribution_name)
    if test_path.is_dir():
        if not list(test_path.iterdir()):
            raise RuntimeError(f"{distribution_name!r} has a '{TEST_CASES_DIR}' directory but it is empty!")
        return DistributionTests(distribution_name, test_path)
    raise RuntimeError(f"No test cases found for {distribution_name!r}!")


def tests_path(distribution_name: str) -> Path:
    if distribution_name == "stdlib":
        return STDLIB_PATH / TESTS_DIR
    else:
        return STUBS_PATH / distribution_name / TESTS_DIR


def test_cases_path(distribution_name: str) -> Path:
    return tests_path(distribution_name) / TEST_CASES_DIR


def get_all_testcase_directories() -> list[DistributionTests]:
    testcase_directories: list[DistributionTests] = []
    for distribution_path in STUBS_PATH.iterdir():
        try:
            pkg_info = distribution_info(distribution_path.name)
        except RuntimeError:
            continue
        testcase_directories.append(pkg_info)
    return [distribution_info("stdlib"), *sorted(testcase_directories)]


def allowlists_path(distribution_name: str) -> Path:
    if distribution_name == "stdlib":
        return tests_path("stdlib") / "stubtest_allowlists"
    else:
        return tests_path(distribution_name)


def allowlists(distribution_name: str) -> list[str]:
    prefix = "" if distribution_name == "stdlib" else "stubtest_allowlist_"
    version_id = f"py{sys.version_info.major}{sys.version_info.minor}"

    platform_allowlist = f"{prefix}{sys.platform}.txt"
    version_allowlist = f"{prefix}{version_id}.txt"
    combined_allowlist = f"{prefix}{sys.platform}-{version_id}.txt"
    local_version_allowlist = version_allowlist + ".local"

    if distribution_name == "stdlib":
        return ["common.txt", platform_allowlist, version_allowlist, combined_allowlist, local_version_allowlist]
    else:
        return ["stubtest_allowlist.txt", platform_allowlist]


# ====================================================================
# Parsing .gitignore
# ====================================================================


@cache
def get_gitignore_spec() -> pathspec.PathSpec:
    with open(".gitignore", encoding="UTF-8") as f:
        return pathspec.PathSpec.from_lines("gitwildmatch", f.readlines())


def spec_matches_path(spec: pathspec.PathSpec, path: Path) -> bool:
    normalized_path = path.as_posix()
    if path.is_dir():
        normalized_path += "/"
    return spec.match_file(normalized_path)


# ====================================================================
# mypy/stubtest call
# ====================================================================


def allowlist_stubtest_arguments(distribution_name: str) -> list[str]:
    stubtest_arguments: list[str] = []
    for allowlist in allowlists(distribution_name):
        path = allowlists_path(distribution_name) / allowlist
        if path.exists():
            stubtest_arguments.extend(["--allowlist", str(path)])
    return stubtest_arguments
