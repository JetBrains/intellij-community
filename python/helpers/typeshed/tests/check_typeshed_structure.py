#!/usr/bin/env python3

"""
Check that the typeshed repository contains the correct files in the
correct places, and that various configuration files are correct.
"""

from __future__ import annotations

import os
import re
from pathlib import Path

from ts_utils.metadata import read_metadata
from ts_utils.paths import REQUIREMENTS_PATH, STDLIB_PATH, STUBS_PATH, TEST_CASES_DIR, TESTS_DIR, tests_path
from ts_utils.utils import (
    get_all_testcase_directories,
    get_gitignore_spec,
    parse_requirements,
    parse_stdlib_versions_file,
    spec_matches_path,
)

extension_descriptions = {".pyi": "stub", ".py": ".py"}

# These type checkers and linters must have exact versions in the requirements file to ensure
# consistent CI runs.
linters = {"mypy", "pyright", "ruff"}

ALLOWED_PY_FILES_IN_TESTS_DIR = {
    "django_settings.py"  # This file contains Django settings used by the mypy_django_plugin during stubtest execution.
}


def assert_consistent_filetypes(
    directory: Path, *, kind: str, allowed: set[str], allow_nonidentifier_filenames: bool = False
) -> None:
    """Check that given directory contains only valid Python files of a certain kind."""
    allowed_paths = {Path(f) for f in allowed}
    contents = list(directory.iterdir())
    gitignore_spec = get_gitignore_spec()
    while contents:
        entry = contents.pop()
        if spec_matches_path(gitignore_spec, entry):
            continue
        if entry.relative_to(directory) in allowed_paths:
            # Note if a subdirectory is allowed, we will not check its contents
            continue
        if entry.is_file():
            if not allow_nonidentifier_filenames:
                assert entry.stem.isidentifier(), f'Files must be valid modules, got: "{entry}"'
            bad_filetype = f'Only {extension_descriptions[kind]!r} files allowed in the "{directory}" directory; got: {entry}'
            assert entry.suffix == kind, bad_filetype
        else:
            assert entry.name.isidentifier(), f"Directories must be valid packages, got: {entry}"
            contents.extend(entry.iterdir())


def check_stdlib() -> None:
    """Check that the stdlib directory contains only the correct files."""
    assert_consistent_filetypes(STDLIB_PATH, kind=".pyi", allowed={"_typeshed/README.md", "VERSIONS", TESTS_DIR})
    check_tests_dir(tests_path("stdlib"))


def check_stubs() -> None:
    """Check that the stubs directory contains only the correct files."""
    gitignore_spec = get_gitignore_spec()
    for dist in STUBS_PATH.iterdir():
        if spec_matches_path(gitignore_spec, dist):
            continue
        assert dist.is_dir(), f"Only directories allowed in stubs, got {dist}"

        valid_dist_name = "^([A-Z0-9]|[A-Z0-9][A-Z0-9._-]*[A-Z0-9])$"  # courtesy of PEP 426
        assert re.fullmatch(
            valid_dist_name, dist.name, re.IGNORECASE
        ), f"Directory name must be a valid distribution name: {dist}"
        assert not dist.name.startswith("types-"), f"Directory name not allowed to start with 'types-': {dist}"

        allowed = {"METADATA.toml", "README", "README.md", "README.rst", TESTS_DIR}
        assert_consistent_filetypes(dist, kind=".pyi", allowed=allowed)

        tests_dir = tests_path(dist.name)
        if tests_dir.exists() and tests_dir.is_dir():
            check_tests_dir(tests_dir)


def check_tests_dir(tests_dir: Path) -> None:
    py_files_present = any(
        file.suffix == ".py" and file.name not in ALLOWED_PY_FILES_IN_TESTS_DIR for file in tests_dir.iterdir()
    )
    error_message = f"Test-case files must be in an `{TESTS_DIR}/{TEST_CASES_DIR}` directory, not in the `{TESTS_DIR}` directory"
    assert not py_files_present, error_message


def check_distutils() -> None:
    """Check whether all setuptools._distutils files are re-exported from distutils."""

    def all_relative_paths_in_directory(path: Path) -> set[Path]:
        return {pyi.relative_to(path) for pyi in path.rglob("*.pyi")}

    setuptools_path = STUBS_PATH / "setuptools" / "setuptools" / "_distutils"
    distutils_path = STUBS_PATH / "setuptools" / "distutils"
    all_setuptools_files = all_relative_paths_in_directory(setuptools_path)
    all_distutils_files = all_relative_paths_in_directory(distutils_path)
    assert all_setuptools_files and all_distutils_files, "Looks like this test might be out of date!"
    extra_files = all_setuptools_files - all_distutils_files
    joined = "\n".join(f"  * {distutils_path / f}" for f in extra_files)
    assert not extra_files, f"Files missing from distutils:\n{joined}"


def check_test_cases() -> None:
    """Check that the test_cases directory contains only the correct files."""
    for _, testcase_dir in get_all_testcase_directories():
        assert_consistent_filetypes(testcase_dir, kind=".py", allowed={"README.md"}, allow_nonidentifier_filenames=True)
        bad_test_case_filename = f'Files in a `{TEST_CASES_DIR}` directory must have names starting with "check_"; got "{{}}"'
        for file in testcase_dir.rglob("*.py"):
            assert file.stem.startswith("check_"), bad_test_case_filename.format(file)


def check_no_symlinks() -> None:
    """Check that there are no symlinks in the typeshed repository."""
    files = [Path(root, file) for root, _, files in os.walk(".") for file in files]
    no_symlink = "You cannot use symlinks in typeshed, please copy {} to its link."
    for file in files:
        if file.suffix == ".pyi" and file.is_symlink():
            raise ValueError(no_symlink.format(file))


def check_versions_file() -> None:
    """Check that the stdlib/VERSIONS file has the correct format."""
    version_map = parse_stdlib_versions_file()
    versions = list(version_map.keys())

    sorted_versions = sorted(versions)
    assert versions == sorted_versions, f"{versions=}\n\n{sorted_versions=}"

    modules = _find_stdlib_modules()
    # Sub-modules don't need to be listed in VERSIONS.
    extra = {m.split(".")[0] for m in modules} - version_map.keys()
    assert not extra, f"Modules not in versions: {extra}"
    extra = version_map.keys() - modules
    assert not extra, f"Versions not in modules: {extra}"


def _find_stdlib_modules() -> set[str]:
    modules = set[str]()
    for path, _, files in os.walk(STDLIB_PATH):
        for filename in files:
            base_module = ".".join(Path(path).parts[1:])
            if filename == "__init__.pyi":
                modules.add(base_module)
            elif filename.endswith(".pyi"):
                mod = filename[:-4]
                modules.add(f"{base_module}.{mod}" if base_module else mod)
    return modules


def check_metadata() -> None:
    """Check that all METADATA.toml files are valid."""
    for distribution in os.listdir(STUBS_PATH):
        # This function does various sanity checks for METADATA.toml files
        read_metadata(distribution)


def check_requirement_pins() -> None:
    """Check that type checkers and linters are pinned to an exact version."""
    requirements = parse_requirements()
    for package in linters:
        assert package in requirements, f"type checker/linter '{package}' not found in {REQUIREMENTS_PATH.name}"
        spec = requirements[package].specifier
        assert len(spec) == 1, f"type checker/linter '{package}' has complex specifier in {REQUIREMENTS_PATH.name}"
        msg = f"type checker/linter '{package}' is not pinned to an exact version in {REQUIREMENTS_PATH.name}"
        assert str(spec).startswith("=="), msg


if __name__ == "__main__":
    check_versions_file()
    check_metadata()
    check_requirement_pins()
    check_no_symlinks()
    check_stdlib()
    check_stubs()
    check_distutils()
    check_test_cases()
