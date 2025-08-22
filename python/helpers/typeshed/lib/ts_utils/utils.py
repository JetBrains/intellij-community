"""Utilities that are imported by multiple scripts in the tests directory."""

from __future__ import annotations

import functools
import re
import sys
import tempfile
from collections.abc import Iterable, Mapping
from pathlib import Path
from types import MethodType
from typing import TYPE_CHECKING, Any, Final, NamedTuple
from typing_extensions import TypeAlias

import pathspec
from packaging.requirements import Requirement

from .paths import GITIGNORE_PATH, REQUIREMENTS_PATH, STDLIB_PATH, STUBS_PATH, TEST_CASES_DIR, allowlists_path, test_cases_path

if TYPE_CHECKING:
    from _typeshed import OpenTextMode

try:
    from termcolor import colored as colored  # pyright: ignore[reportAssignmentType]
except ImportError:

    def colored(text: str, color: str | None = None, **kwargs: Any) -> str:  # type: ignore[misc] # noqa: ARG001
        return text


PYTHON_VERSION: Final = f"{sys.version_info.major}.{sys.version_info.minor}"


def strip_comments(text: str) -> str:
    return text.split("#")[0].strip()


# ====================================================================
# Printing utilities
# ====================================================================


def print_command(cmd: str | Iterable[str]) -> None:
    if not isinstance(cmd, str):
        cmd = " ".join(cmd)
    print(colored(f"Running: {cmd}", "blue"))


def print_info(message: str) -> None:
    print(colored(message, "blue"))


def print_warning(message: str) -> None:
    print(colored(message, "yellow"))


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


def print_time(t: float) -> None:
    print(f"({t:.2f} s) ", end="")


# ====================================================================
# Dynamic venv creation
# ====================================================================


@functools.cache
def venv_python(venv_dir: Path) -> Path:
    if sys.platform == "win32":
        return venv_dir / "Scripts" / "python.exe"
    return venv_dir / "bin" / "python"


# ====================================================================
# Parsing the requirements file
# ====================================================================


@functools.cache
def parse_requirements() -> Mapping[str, Requirement]:
    """Return a dictionary of requirements from the requirements file."""
    with REQUIREMENTS_PATH.open(encoding="UTF-8") as requirements_file:
        stripped_lines = map(strip_comments, requirements_file)
        stripped_more = [li for li in stripped_lines if not li.startswith("-")]
        requirements = map(Requirement, filter(None, stripped_more))
        return {requirement.name: requirement for requirement in requirements}


def get_mypy_req() -> str:
    return str(parse_requirements()["mypy"])


# ====================================================================
# Parsing the stdlib/VERSIONS file
# ====================================================================

VersionTuple: TypeAlias = tuple[int, int]
SupportedVersionsDict: TypeAlias = dict[str, tuple[VersionTuple, VersionTuple]]

VERSIONS_PATH = STDLIB_PATH / "VERSIONS"
VERSION_LINE_RE = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_.]*): ([23]\.\d{1,2})-([23]\.\d{1,2})?$")
VERSION_RE = re.compile(r"^([23])\.(\d+)$")


def parse_stdlib_versions_file() -> SupportedVersionsDict:
    result: dict[str, tuple[VersionTuple, VersionTuple]] = {}
    with VERSIONS_PATH.open(encoding="UTF-8") as f:
        for line in f:
            stripped_line = strip_comments(line)
            if stripped_line == "":
                continue
            m = VERSION_LINE_RE.match(stripped_line)
            assert m, f"invalid VERSIONS line: {stripped_line}"
            mod: str = m.group(1)
            assert mod not in result, f"Duplicate module {mod} in VERSIONS"
            min_version = _parse_version(m.group(2))
            max_version = _parse_version(m.group(3)) if m.group(3) else (99, 99)
            result[mod] = min_version, max_version
    return result


def supported_versions_for_module(module_versions: SupportedVersionsDict, module_name: str) -> tuple[VersionTuple, VersionTuple]:
    while "." in module_name:
        if module_name in module_versions:
            return module_versions[module_name]
        module_name = ".".join(module_name.split(".")[:-1])
    return module_versions[module_name]


def _parse_version(v_str: str) -> tuple[int, int]:
    m = VERSION_RE.match(v_str)
    assert m, f"invalid version: {v_str}"
    return int(m.group(1)), int(m.group(2))


# ====================================================================
# Test Directories
# ====================================================================


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


def get_all_testcase_directories() -> list[DistributionTests]:
    testcase_directories: list[DistributionTests] = []
    for distribution_path in STUBS_PATH.iterdir():
        try:
            pkg_info = distribution_info(distribution_path.name)
        except RuntimeError:
            continue
        testcase_directories.append(pkg_info)
    return [distribution_info("stdlib"), *sorted(testcase_directories)]


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


# Re-exposing as a public name to avoid many pyright reportPrivateUsage
TemporaryFileWrapper = tempfile._TemporaryFileWrapper  # pyright: ignore[reportPrivateUsage]

# We need to work around a limitation of tempfile.NamedTemporaryFile on Windows
# For details, see https://github.com/python/typeshed/pull/13620#discussion_r1990185997
# Python 3.12 added a cross-platform solution with `tempfile.NamedTemporaryFile("w+", delete_on_close=False)`
if sys.platform != "win32":
    NamedTemporaryFile = tempfile.NamedTemporaryFile  # noqa: TID251
else:

    def NamedTemporaryFile(mode: OpenTextMode) -> TemporaryFileWrapper[str]:  # noqa: N802
        def close(self: TemporaryFileWrapper[str]) -> None:
            TemporaryFileWrapper.close(self)  # pyright: ignore[reportUnknownMemberType]
            Path(self.name).unlink()

        temp = tempfile.NamedTemporaryFile(mode, delete=False)  # noqa: SIM115, TID251
        temp.close = MethodType(close, temp)  # type: ignore[method-assign]
        return temp


# ====================================================================
# Parsing .gitignore
# ====================================================================


@functools.cache
def get_gitignore_spec() -> pathspec.PathSpec:
    with GITIGNORE_PATH.open(encoding="UTF-8") as f:
        return pathspec.GitIgnoreSpec.from_lines(f)


def spec_matches_path(spec: pathspec.PathSpec, path: Path) -> bool:
    normalized_path = path.as_posix()
    if path.is_dir():
        normalized_path += "/"
    return spec.match_file(normalized_path)


# ====================================================================
# stubtest call
# ====================================================================


def allowlist_stubtest_arguments(distribution_name: str) -> list[str]:
    stubtest_arguments: list[str] = []
    for allowlist in allowlists(distribution_name):
        path = allowlists_path(distribution_name) / allowlist
        if path.exists():
            stubtest_arguments.extend(["--allowlist", str(path)])
    return stubtest_arguments
