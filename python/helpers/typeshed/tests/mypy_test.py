#!/usr/bin/env python3
"""Run mypy on typeshed's stdlib and third-party stubs."""

from __future__ import annotations

import argparse
import concurrent.futures
import os
import subprocess
import sys
import tempfile
import time
from collections import defaultdict
from dataclasses import dataclass
from enum import Enum
from itertools import product
from pathlib import Path
from threading import Lock
from typing import Annotated, Any, NamedTuple
from typing_extensions import TypeAlias

from packaging.requirements import Requirement

from ts_utils.metadata import PackageDependencies, get_recursive_requirements, read_metadata
from ts_utils.mypy import MypyDistConf, mypy_configuration_from_distribution, temporary_mypy_config_file
from ts_utils.paths import STDLIB_PATH, STUBS_PATH, TESTS_DIR, TS_BASE_PATH, distribution_path
from ts_utils.utils import (
    PYTHON_VERSION,
    colored,
    get_gitignore_spec,
    get_mypy_req,
    parse_stdlib_versions_file,
    print_error,
    print_success_msg,
    spec_matches_path,
    supported_versions_for_module,
    venv_python,
)

# Fail early if mypy isn't installed
try:
    import mypy  # pyright: ignore[reportUnusedImport]  # noqa: F401
except ImportError:
    print_error("Cannot import mypy. Did you install it?")
    sys.exit(1)

SUPPORTED_VERSIONS = ["3.14", "3.13", "3.12", "3.11", "3.10", "3.9"]
SUPPORTED_PLATFORMS = ("linux", "win32", "darwin")
DIRECTORIES_TO_TEST = [STDLIB_PATH, STUBS_PATH]

VersionString: TypeAlias = Annotated[str, "Must be one of the entries in SUPPORTED_VERSIONS"]
Platform: TypeAlias = Annotated[str, "Must be one of the entries in SUPPORTED_PLATFORMS"]


@dataclass(init=False)
class CommandLineArgs:
    verbose: int
    filter: list[Path]
    exclude: list[Path] | None
    python_version: list[VersionString] | None
    platform: list[Platform] | None


def valid_path(cmd_arg: str) -> Path:
    """Parse a CLI argument that is intended to point to a valid typeshed path."""
    path = Path(cmd_arg)
    if not path.exists():
        raise argparse.ArgumentTypeError(f'"{path}" does not exist in typeshed!')
    if not (path in DIRECTORIES_TO_TEST or any(directory in path.parents for directory in DIRECTORIES_TO_TEST)):
        raise argparse.ArgumentTypeError('mypy_test.py only tests the stubs found in the "stdlib" and "stubs" directories')
    return path


def remove_dev_suffix(version: str) -> str:
    """Remove the `-dev` suffix from a version string.

    This is a helper function for argument-parsing.
    """
    if version.endswith("-dev"):
        return version[: -len("-dev")]
    return ".".join(version.split(".")[:2])


parser = argparse.ArgumentParser(
    description="Typecheck typeshed's stubs with mypy. Patterns are unanchored regexps on the full path."
)
parser.add_argument(
    "filter",
    type=valid_path,
    nargs="*",
    help='Test these files and directories (defaults to all files in the "stdlib" and "stubs" directories)',
)
parser.add_argument("-x", "--exclude", type=valid_path, nargs="*", help="Exclude these files and directories")
parser.add_argument("-v", "--verbose", action="count", default=0, help="More output")
parser.add_argument(
    "-p",
    "--python-version",
    type=remove_dev_suffix,
    choices=SUPPORTED_VERSIONS,
    nargs="*",
    action="extend",
    help="These versions only (major[.minor])",
)
parser.add_argument(
    "--platform",
    choices=SUPPORTED_PLATFORMS,
    nargs="*",
    action="extend",
    help="Run mypy for certain OS platforms (defaults to sys.platform only)",
)


@dataclass
class TestConfig:
    """Configuration settings for a single run of the `test_typeshed` function."""

    verbose: int
    filter: list[Path]
    exclude: list[Path]
    version: VersionString
    platform: Platform


def log(args: TestConfig, *varargs: object) -> None:
    if args.verbose >= 2:
        print(colored(" ".join(map(str, varargs)), "blue"))


def match(path: Path, args: TestConfig) -> bool:
    for excluded_path in args.exclude:
        if path == excluded_path:
            log(args, path, "explicitly excluded")
            return False
        if excluded_path in path.parents:
            log(args, path, f'is in an explicitly excluded directory "{excluded_path}"')
            return False
    for included_path in args.filter:
        if path == included_path:
            log(args, path, "was explicitly included")
            return True
        if included_path in path.parents:
            log(args, path, f'is in an explicitly included directory "{included_path}"')
            return True
    log_msg = (
        f'is implicitly excluded: was not in any of the directories or paths specified on the command line: "{args.filter!r}"'
    )
    log(args, path, log_msg)
    return False


def add_files(files: list[Path], module: Path, args: TestConfig) -> None:
    """Add all files in package or module represented by 'name' located in 'root'."""
    if module.name.startswith("."):
        return
    if module.is_file() and module.suffix == ".pyi":
        if match(module, args):
            files.append(module)
    else:
        files.extend(sorted(file for file in module.rglob("*.pyi") if match(file, args)))


class MypyResult(Enum):
    SUCCESS = 0
    FAILURE = 1
    CRASH = 2

    @staticmethod
    def from_process_result(result: subprocess.CompletedProcess[Any]) -> MypyResult:
        if result.returncode == 0:
            return MypyResult.SUCCESS
        elif result.returncode == 1:
            return MypyResult.FAILURE
        else:
            return MypyResult.CRASH


def run_mypy(
    args: TestConfig,
    configurations: list[MypyDistConf],
    files: list[Path],
    *,
    testing_stdlib: bool,
    non_types_dependencies: bool,
    venv_dir: Path | None,
    mypypath: str | None = None,
) -> MypyResult:
    env_vars = dict(os.environ)
    if mypypath is not None:
        env_vars["MYPYPATH"] = mypypath
    with temporary_mypy_config_file(configurations) as temp:
        flags = [
            "--python-version",
            args.version,
            "--show-traceback",
            "--warn-incomplete-stub",
            "--no-error-summary",
            "--platform",
            args.platform,
            "--custom-typeshed-dir",
            str(TS_BASE_PATH),
            "--strict",
            # Stub completion is checked by pyright (--allow-*-defs)
            "--allow-untyped-defs",
            "--allow-incomplete-defs",
            # See https://github.com/python/typeshed/pull/9491#issuecomment-1381574946
            # for discussion and reasoning to keep "--allow-subclassing-any"
            "--allow-subclassing-any",
            "--enable-error-code",
            "ignore-without-code",
            "--enable-error-code",
            "redundant-self",
            "--config-file",
            temp.name,
        ]
        if not testing_stdlib:
            flags.append("--explicit-package-bases")
        if not non_types_dependencies:
            flags.append("--no-site-packages")

        mypy_args = [*flags, *map(str, files)]
        python_path = sys.executable if venv_dir is None else str(venv_python(venv_dir))
        mypy_command = [python_path, "-m", "mypy", *mypy_args]
        if args.verbose:
            print(colored(f"running {' '.join(mypy_command)}", "blue"))
        result = subprocess.run(mypy_command, capture_output=True, text=True, env=env_vars, check=False)
    if result.returncode:
        print_error(f"failure (exit code {result.returncode})\n")
        if result.stdout:
            print_error(result.stdout)
        if result.stderr:
            print_error(result.stderr)
        if non_types_dependencies and args.verbose:
            print("Ran with the following environment:")
            subprocess.run(["uv", "pip", "freeze"], env={**os.environ, "VIRTUAL_ENV": str(venv_dir)}, check=False)
            print()
    else:
        print_success_msg()

    return MypyResult.from_process_result(result)


def add_third_party_files(distribution: str, files: list[Path], args: TestConfig, seen_dists: set[str]) -> None:
    typeshed_reqs = get_recursive_requirements(distribution).typeshed_pkgs
    if distribution in seen_dists:
        return
    seen_dists.add(distribution)
    seen_dists.update(r.name for r in typeshed_reqs)
    root = distribution_path(distribution)
    for path in root.iterdir():
        add_files(files, path, args)


class TestResult(NamedTuple):
    mypy_result: MypyResult
    files_checked: int


def test_third_party_distribution(
    distribution: str, args: TestConfig, venv_dir: Path | None, *, non_types_dependencies: bool
) -> TestResult:
    """Test the stubs of a third-party distribution.

    Return a tuple, where the first element indicates mypy's return code
    and the second element is the number of checked files.
    """
    files: list[Path] = []
    seen_dists: set[str] = set()
    add_third_party_files(distribution, files, args, seen_dists)
    configurations = mypy_configuration_from_distribution(distribution)

    if not files and args.filter:
        return TestResult(MypyResult.SUCCESS, 0)

    print(f"testing {distribution} ({len(files)} files)... ", end="", flush=True)

    if not files:
        print_error("no files found")
        sys.exit(1)

    mypypath = os.pathsep.join(str(distribution_path(dist)) for dist in seen_dists)
    if args.verbose:
        print(colored(f"\nMYPYPATH={mypypath}", "blue"))
    result = run_mypy(
        args,
        configurations,
        files,
        venv_dir=venv_dir,
        mypypath=mypypath,
        testing_stdlib=False,
        non_types_dependencies=non_types_dependencies,
    )
    return TestResult(result, len(files))


def test_stdlib(args: TestConfig) -> TestResult:
    files: list[Path] = []
    for file in STDLIB_PATH.iterdir():
        if file.name in ("VERSIONS", TESTS_DIR):
            continue
        add_files(files, file, args)

    files = remove_modules_not_in_python_version(files, args.version)

    if not files:
        return TestResult(MypyResult.SUCCESS, 0)

    print(f"Testing stdlib ({len(files)} files)... ", end="", flush=True)
    # We don't actually need to install anything for the stdlib testing
    result = run_mypy(args, [], files, venv_dir=None, testing_stdlib=True, non_types_dependencies=False)
    return TestResult(result, len(files))


def remove_modules_not_in_python_version(paths: list[Path], py_version: VersionString) -> list[Path]:
    py_version_tuple = tuple(map(int, py_version.split(".")))
    module_versions = parse_stdlib_versions_file()
    new_paths: list[Path] = []
    for path in paths:
        if path.parts[0] != "stdlib" or path.suffix != ".pyi":
            continue
        module_name = stdlib_module_name_from_path(path)
        min_version, max_version = supported_versions_for_module(module_versions, module_name)
        if min_version <= py_version_tuple <= max_version:
            new_paths.append(path)
    return new_paths


def stdlib_module_name_from_path(path: Path) -> str:
    assert path.parts[0] == "stdlib"
    assert path.suffix == ".pyi"
    parts = list(path.parts[1:-1])
    if path.parts[-1] != "__init__.pyi":
        parts.append(path.parts[-1].removesuffix(".pyi"))
    return ".".join(parts)


@dataclass
class TestSummary:
    mypy_result: MypyResult = MypyResult.SUCCESS
    files_checked: int = 0
    packages_skipped: int = 0
    packages_with_errors: int = 0

    def register_result(self, mypy_result: MypyResult, files_checked: int) -> None:
        if mypy_result.value > self.mypy_result.value:
            self.mypy_result = mypy_result
        if mypy_result != MypyResult.SUCCESS:
            self.packages_with_errors += 1
        self.files_checked += files_checked

    def skip_package(self) -> None:
        self.packages_skipped += 1

    def merge(self, other: TestSummary) -> None:
        if other.mypy_result.value > self.mypy_result.value:
            self.mypy_result = other.mypy_result
        self.files_checked += other.files_checked
        self.packages_skipped += other.packages_skipped
        self.packages_with_errors += other.packages_with_errors


_PRINT_LOCK = Lock()
_DISTRIBUTION_TO_VENV_MAPPING: dict[str, Path | None] = {}


def setup_venv_for_external_requirements_set(
    requirements_set: frozenset[Requirement], tempdir: Path, args: TestConfig
) -> tuple[frozenset[Requirement], Path]:
    venv_dir = tempdir / f".venv-{hash(requirements_set)}"
    uv_command = ["uv", "venv", str(venv_dir)]
    if not args.verbose:
        uv_command.append("--quiet")
    subprocess.run(uv_command, check=True)
    return requirements_set, venv_dir


def install_requirements_for_venv(venv_dir: Path, args: TestConfig, external_requirements: frozenset[Requirement]) -> None:
    req_args = sorted(str(req) for req in external_requirements)
    # Use --no-cache-dir to avoid issues with concurrent read/writes to the cache
    uv_command = ["uv", "pip", "install", get_mypy_req(), *req_args, "--no-cache-dir"]
    if args.verbose:
        with _PRINT_LOCK:
            print(colored(f"Running {uv_command}", "blue"))
    else:
        uv_command.append("--quiet")
    try:
        subprocess.run(uv_command, check=True, text=True, env={**os.environ, "VIRTUAL_ENV": str(venv_dir)})
    except subprocess.CalledProcessError as e:
        print(e.stderr)
        raise


def setup_virtual_environments(distributions: dict[str, PackageDependencies], args: TestConfig, tempdir: Path) -> None:
    """Logic necessary for testing stubs with non-types dependencies in isolated environments."""
    if not distributions:
        return  # hooray! Nothing to do

    # STAGE 1: Determine which (if any) stubs packages require virtual environments.
    # Group stubs packages according to their external-requirements sets
    external_requirements_to_distributions: defaultdict[frozenset[Requirement], list[str]] = defaultdict(list)
    num_pkgs_with_external_reqs = 0

    for distribution_name, requirements in distributions.items():
        if requirements.external_pkgs:
            num_pkgs_with_external_reqs += 1
            external_requirements = frozenset(requirements.external_pkgs)
            external_requirements_to_distributions[external_requirements].append(distribution_name)
        else:
            _DISTRIBUTION_TO_VENV_MAPPING[distribution_name] = None

    # Exit early if there are no stubs packages that have non-types dependencies
    if num_pkgs_with_external_reqs == 0:
        if args.verbose:
            print(colored("No additional venvs are required to be set up", "blue"))
        return

    # STAGE 2: Setup a virtual environment for each unique set of external requirements
    requirements_sets_to_venvs: dict[frozenset[Requirement], Path] = {}

    if args.verbose:
        num_venvs = len(external_requirements_to_distributions)
        msg = (
            f"Setting up {num_venvs} venv{'s' if num_venvs != 1 else ''} "
            f"for {num_pkgs_with_external_reqs} "
            f"distribution{'s' if num_pkgs_with_external_reqs != 1 else ''}... "
        )
        print(colored(msg, "blue"), end="", flush=True)

    venv_start_time = time.perf_counter()

    with concurrent.futures.ProcessPoolExecutor() as executor:
        venv_futures = [
            executor.submit(setup_venv_for_external_requirements_set, requirements_set, tempdir, args)
            for requirements_set in external_requirements_to_distributions
        ]
        for venv_future in concurrent.futures.as_completed(venv_futures):
            requirements_set, venv_dir = venv_future.result()
            requirements_sets_to_venvs[requirements_set] = venv_dir

    venv_elapsed_time = time.perf_counter() - venv_start_time

    if args.verbose:
        print(colored(f"took {venv_elapsed_time:.2f} seconds", "blue"))

    # STAGE 3: For each {virtual_environment: requirements_set} pairing,
    # `uv pip install` the requirements set into the virtual environment
    pip_start_time = time.perf_counter()

    # Limit workers to 10 at a time, since this makes network requests
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        pip_install_futures = [
            executor.submit(install_requirements_for_venv, venv_dir, args, requirements_set)
            for requirements_set, venv_dir in requirements_sets_to_venvs.items()
        ]
        concurrent.futures.wait(pip_install_futures)

    pip_elapsed_time = time.perf_counter() - pip_start_time

    if args.verbose:
        msg = f"Combined time for installing requirements across all venvs: {pip_elapsed_time:.2f} seconds"
        print(colored(msg, "blue"))

    # STAGE 4: Populate the _DISTRIBUTION_TO_VENV_MAPPING
    # so that we have a simple {distribution: venv_to_use} mapping to use for the rest of the test.
    for requirements_set, distribution_list in external_requirements_to_distributions.items():
        venv_to_use = requirements_sets_to_venvs[requirements_set]
        _DISTRIBUTION_TO_VENV_MAPPING.update(dict.fromkeys(distribution_list, venv_to_use))


def test_third_party_stubs(args: TestConfig, tempdir: Path) -> TestSummary:
    print("Testing third-party packages...")
    summary = TestSummary()
    gitignore_spec = get_gitignore_spec()
    distributions_to_check: dict[str, PackageDependencies] = {}

    for distribution in sorted(os.listdir("stubs")):
        dist_path = distribution_path(distribution)

        if spec_matches_path(gitignore_spec, dist_path):
            continue

        if dist_path in args.filter or STUBS_PATH in args.filter or any(dist_path in path.parents for path in args.filter):
            metadata = read_metadata(distribution)
            if not metadata.requires_python.contains(PYTHON_VERSION):
                msg = (
                    f"skipping {distribution!r} (requires Python {metadata.requires_python}; "
                    f"test is being run using Python {PYTHON_VERSION})"
                )
                print(colored(msg, "yellow"))
                summary.skip_package()
                continue
            if not metadata.requires_python.contains(args.version):
                msg = f"skipping {distribution!r} for target Python {args.version} (requires Python {metadata.requires_python})"
                print(colored(msg, "yellow"))
                summary.skip_package()
                continue

            distributions_to_check[distribution] = get_recursive_requirements(distribution)

    # Setup the necessary virtual environments for testing the third-party stubs.
    # Note that some stubs may not be tested on all Python versions
    # (due to version incompatibilities),
    # so we can't guarantee that setup_virtual_environments()
    # will only be called once per session.
    distributions_without_venv = {
        distribution: requirements
        for distribution, requirements in distributions_to_check.items()
        if distribution not in _DISTRIBUTION_TO_VENV_MAPPING
    }
    setup_virtual_environments(distributions_without_venv, args, tempdir)

    # Check that there is a venv for every distribution we're testing.
    # Some venvs may exist from previous runs but are skipped in this run.
    assert _DISTRIBUTION_TO_VENV_MAPPING.keys() >= distributions_to_check.keys()

    for distribution in distributions_to_check:
        venv_dir = _DISTRIBUTION_TO_VENV_MAPPING[distribution]
        non_types_dependencies = venv_dir is not None
        mypy_result, files_checked = test_third_party_distribution(
            distribution, args, venv_dir=venv_dir, non_types_dependencies=non_types_dependencies
        )
        summary.register_result(mypy_result, files_checked)

    return summary


def test_typeshed(args: TestConfig, tempdir: Path) -> TestSummary:
    print(f"*** Testing Python {args.version} on {args.platform}")
    summary = TestSummary()

    if STDLIB_PATH in args.filter or any(STDLIB_PATH in path.parents for path in args.filter):
        mypy_result, files_checked = test_stdlib(args)
        summary.register_result(mypy_result, files_checked)
        print()

    if STUBS_PATH in args.filter or any(STUBS_PATH in path.parents for path in args.filter):
        tp_results = test_third_party_stubs(args, tempdir)
        summary.merge(tp_results)
        print()

    return summary


def main() -> None:
    args = parser.parse_args(namespace=CommandLineArgs())
    versions = args.python_version or SUPPORTED_VERSIONS
    platforms = args.platform or [sys.platform]
    path_filter = args.filter or DIRECTORIES_TO_TEST
    exclude = args.exclude or []
    summary = TestSummary()
    with tempfile.TemporaryDirectory() as td:
        td_path = Path(td)
        for version, platform in product(versions, platforms):
            config = TestConfig(args.verbose, path_filter, exclude, version, platform)
            version_summary = test_typeshed(args=config, tempdir=td_path)
            summary.merge(version_summary)

    if summary.mypy_result == MypyResult.FAILURE:
        plural1 = "" if summary.packages_with_errors == 1 else "s"
        plural2 = "" if summary.files_checked == 1 else "s"
        print_error(
            f"--- {summary.packages_with_errors} package{plural1} with errors, {summary.files_checked} file{plural2} checked ---"
        )
        sys.exit(1)
    if summary.mypy_result == MypyResult.CRASH:
        plural = "" if summary.files_checked == 1 else "s"
        print_error(f"--- mypy crashed, {summary.files_checked} file{plural} checked ---")
        sys.exit(2)
    if summary.packages_skipped:
        plural = "" if summary.packages_skipped == 1 else "s"
        print(colored(f"--- {summary.packages_skipped} package{plural} skipped ---", "yellow"))
    if summary.files_checked:
        plural = "" if summary.files_checked == 1 else "s"
        print(colored(f"--- success, {summary.files_checked} file{plural} checked ---", "green"))
    else:
        print_error("--- nothing to do; exit 1 ---")
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_error("\n\nTest aborted due to KeyboardInterrupt!")
        sys.exit(1)
