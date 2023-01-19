#!/usr/bin/env python3
"""Run mypy on various typeshed directories, with varying command-line arguments.

Depends on mypy being installed.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Iterable
from contextlib import redirect_stderr, redirect_stdout
from dataclasses import dataclass
from io import StringIO
from itertools import product
from pathlib import Path
from typing import TYPE_CHECKING, NamedTuple

if TYPE_CHECKING:
    from _typeshed import StrPath

from typing_extensions import Annotated, TypeAlias

import tomli
from colors import colored, print_error, print_success_msg

SUPPORTED_VERSIONS = [(3, 11), (3, 10), (3, 9), (3, 8), (3, 7), (2, 7)]
SUPPORTED_PLATFORMS = frozenset({"linux", "win32", "darwin"})
TYPESHED_DIRECTORIES = frozenset({"stdlib", "stubs", "tests", "test_cases", "scripts"})

MajorVersion: TypeAlias = int
MinorVersion: TypeAlias = int
Platform: TypeAlias = Annotated[str, "Must be one of the entries in SUPPORTED_PLATFORMS"]
Directory: TypeAlias = Annotated[str, "Must be one of the entries in TYPESHED_DIRECTORIES"]


def python_version(arg: str) -> tuple[MajorVersion, MinorVersion]:
    version = tuple(map(int, arg.split(".")))  # This will naturally raise TypeError if it's not in the form "{major}.{minor}"
    if version not in SUPPORTED_VERSIONS:
        raise ValueError
    # mypy infers the return type as tuple[int, ...]
    return version  # type: ignore[return-value]


def python_platform(platform: str) -> str:
    if platform not in SUPPORTED_PLATFORMS:
        raise ValueError
    return platform


def typeshed_directory(directory: str) -> str:
    if directory not in TYPESHED_DIRECTORIES:
        raise ValueError
    return directory


class CommandLineArgs(argparse.Namespace):
    verbose: int
    dry_run: bool
    exclude: list[str] | None
    python_version: list[tuple[MajorVersion, MinorVersion]] | None
    dir: list[Directory] | None
    platform: list[Platform] | None
    filter: list[str]


parser = argparse.ArgumentParser(description="Test runner for typeshed. Patterns are unanchored regexps on the full path.")
parser.add_argument("-v", "--verbose", action="count", default=0, help="More output")
parser.add_argument("-n", "--dry-run", action="store_true", help="Don't actually run mypy")
parser.add_argument("-x", "--exclude", type=str, nargs="*", help="Exclude pattern")
parser.add_argument(
    "-p", "--python-version", type=python_version, nargs="*", action="extend", help="These versions only (major[.minor])"
)
parser.add_argument(
    "-d",
    "--dir",
    type=typeshed_directory,
    nargs="*",
    action="extend",
    help="Test only these top-level typeshed directories (defaults to all typeshed directories)",
)
parser.add_argument(
    "--platform",
    type=python_platform,
    nargs="*",
    action="extend",
    help="Run mypy for certain OS platforms (defaults to sys.platform only)",
)
parser.add_argument("filter", type=str, nargs="*", help="Include pattern (default all)")


@dataclass
class TestConfig:
    """Configuration settings for a single run of the `test_typeshed` function."""

    verbose: int
    dry_run: bool
    exclude: list[str] | None
    major: MajorVersion
    minor: MinorVersion
    directories: frozenset[Directory]
    platform: Platform
    filter: list[str]


def log(args: TestConfig, *varargs: object) -> None:
    if args.verbose >= 2:
        print(*varargs)


def match(fn: str, args: TestConfig) -> bool:
    if not args.filter and not args.exclude:
        log(args, fn, "accept by default")
        return True
    if args.exclude:
        for f in args.exclude:
            if re.search(f, fn):
                log(args, fn, "excluded by pattern", f)
                return False
    if args.filter:
        for f in args.filter:
            if re.search(f, fn):
                log(args, fn, "accepted by pattern", f)
                return True
    if args.filter:
        log(args, fn, "rejected (no pattern matches)")
        return False
    log(args, fn, "accepted (no exclude pattern matches)")
    return True


_VERSION_LINE_RE = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_.]*): ([23]\.\d{1,2})-([23]\.\d{1,2})?$")
MinVersion: TypeAlias = tuple[MajorVersion, MinorVersion]
MaxVersion: TypeAlias = tuple[MajorVersion, MinorVersion]


def parse_versions(fname: StrPath) -> dict[str, tuple[MinVersion, MaxVersion]]:
    result = {}
    with open(fname) as f:
        for line in f:
            # Allow having some comments or empty lines.
            line = line.split("#")[0].strip()
            if line == "":
                continue
            m = _VERSION_LINE_RE.match(line)
            assert m, "invalid VERSIONS line: " + line
            mod: str = m.group(1)
            min_version = parse_version(m.group(2))
            max_version = parse_version(m.group(3)) if m.group(3) else (99, 99)
            result[mod] = min_version, max_version
    return result


_VERSION_RE = re.compile(r"^([23])\.(\d+)$")


def parse_version(v_str: str) -> tuple[int, int]:
    m = _VERSION_RE.match(v_str)
    assert m, "invalid version: " + v_str
    return int(m.group(1)), int(m.group(2))


def add_files(files: list[str], seen: set[str], root: str, name: str, args: TestConfig) -> None:
    """Add all files in package or module represented by 'name' located in 'root'."""
    full = os.path.join(root, name)
    mod, ext = os.path.splitext(name)
    if ext in [".pyi", ".py"]:
        if match(full, args):
            seen.add(mod)
            files.append(full)
    elif os.path.isfile(os.path.join(full, "__init__.pyi")) or os.path.isfile(os.path.join(full, "__init__.py")):
        for r, ds, fs in os.walk(full):
            ds.sort()
            fs.sort()
            for f in fs:
                m, x = os.path.splitext(f)
                if x in [".pyi", ".py"]:
                    fn = os.path.join(r, f)
                    if match(fn, args):
                        seen.add(mod)
                        files.append(fn)


class MypyDistConf(NamedTuple):
    module_name: str
    values: dict


# The configuration section in the metadata file looks like the following, with multiple module sections possible
# [mypy-tests]
# [mypy-tests.yaml]
# module_name = "yaml"
# [mypy-tests.yaml.values]
# disallow_incomplete_defs = true
# disallow_untyped_defs = true


def add_configuration(configurations: list[MypyDistConf], distribution: str) -> None:
    with open(os.path.join("stubs", distribution, "METADATA.toml")) as f:
        data = dict(tomli.loads(f.read()))

    mypy_tests_conf = data.get("mypy-tests")
    if not mypy_tests_conf:
        return

    assert isinstance(mypy_tests_conf, dict), "mypy-tests should be a section"
    for section_name, mypy_section in mypy_tests_conf.items():
        assert isinstance(mypy_section, dict), f"{section_name} should be a section"
        module_name = mypy_section.get("module_name")

        assert module_name is not None, f"{section_name} should have a module_name key"
        assert isinstance(module_name, str), f"{section_name} should be a key-value pair"

        values = mypy_section.get("values")
        assert values is not None, f"{section_name} should have a values section"
        assert isinstance(values, dict), "values should be a section"

        configurations.append(MypyDistConf(module_name, values.copy()))


def run_mypy(args: TestConfig, configurations: list[MypyDistConf], files: list[str], *, custom_typeshed: bool = False) -> int:
    try:
        from mypy.api import run as mypy_run
    except ImportError:
        print_error("Cannot import mypy. Did you install it?")
        sys.exit(1)

    with tempfile.NamedTemporaryFile("w+") as temp:
        temp.write("[mypy]\n")
        for dist_conf in configurations:
            temp.write(f"[mypy-{dist_conf.module_name}]\n")
            for k, v in dist_conf.values.items():
                temp.write(f"{k} = {v}\n")
        temp.flush()

        flags = get_mypy_flags(args, temp.name, custom_typeshed=custom_typeshed)
        mypy_args = [*flags, *files]
        if args.verbose:
            print("running mypy", " ".join(mypy_args))
        if args.dry_run:
            exit_code = 0
        else:
            stdout_redirect, stderr_redirect = StringIO(), StringIO()
            with redirect_stdout(stdout_redirect), redirect_stderr(stderr_redirect):
                returned_stdout, returned_stderr, exit_code = mypy_run(mypy_args)

            if exit_code:
                print_error("failure\n")
                captured_stdout = stdout_redirect.getvalue()
                captured_stderr = stderr_redirect.getvalue()
                if returned_stderr:
                    print_error(returned_stderr)
                if captured_stderr:
                    print_error(captured_stderr)
                if returned_stdout:
                    print_error(returned_stdout)
                if captured_stdout:
                    print_error(captured_stdout, end="")
            else:
                print_success_msg()
        return exit_code


ReturnCode: TypeAlias = int


def run_mypy_as_subprocess(directory: StrPath, flags: Iterable[str]) -> ReturnCode:
    result = subprocess.run([sys.executable, "-m", "mypy", directory, *flags], capture_output=True)
    stdout, stderr = result.stdout, result.stderr
    if stderr:
        print_error(stderr.decode())
    if stdout:
        print_error(stdout.decode())
    return result.returncode


def get_mypy_flags(
    args: TestConfig,
    temp_name: str | None,
    *,
    custom_typeshed: bool = False,
    strict: bool = False,
    test_suite_run: bool = False,
    enforce_error_codes: bool = True,
    ignore_missing_imports: bool = False,
) -> list[str]:
    flags = [
        "--python-version",
        f"{args.major}.{args.minor}",
        "--show-traceback",
        "--warn-incomplete-stub",
        "--show-error-codes",
        "--no-error-summary",
        "--platform",
        args.platform,
    ]
    if strict:
        flags.append("--strict")
    else:
        flags.extend(["--no-implicit-optional", "--disallow-untyped-decorators", "--disallow-any-generics", "--strict-equality"])
    if temp_name is not None:
        flags.extend(["--config-file", temp_name])
    if custom_typeshed:
        # Setting custom typeshed dir prevents mypy from falling back to its bundled
        # typeshed in case of stub deletions
        flags.extend(["--custom-typeshed-dir", os.path.dirname(os.path.dirname(__file__))])
    if test_suite_run:
        flags.append("--namespace-packages")
        if args.platform == "win32":
            flags.extend(["--exclude", "tests/pytype_test.py"])
    else:
        flags.append("--no-site-packages")
    if enforce_error_codes:
        flags.extend(["--enable-error-code", "ignore-without-code"])
    if ignore_missing_imports:
        flags.append("--ignore-missing-imports")
    return flags


def read_dependencies(distribution: str) -> list[str]:
    with open(os.path.join("stubs", distribution, "METADATA.toml")) as f:
        data = dict(tomli.loads(f.read()))
    requires = data.get("requires", [])
    assert isinstance(requires, list)
    dependencies = []
    for dependency in requires:
        assert isinstance(dependency, str)
        assert dependency.startswith("types-")
        dependencies.append(dependency[6:].split("<")[0])
    return dependencies


def add_third_party_files(
    distribution: str, files: list[str], args: TestConfig, configurations: list[MypyDistConf], seen_dists: set[str]
) -> None:
    if distribution in seen_dists:
        return
    seen_dists.add(distribution)

    dependencies = read_dependencies(distribution)
    for dependency in dependencies:
        add_third_party_files(dependency, files, args, configurations, seen_dists)

    root = os.path.join("stubs", distribution)
    for name in os.listdir(root):
        mod, _ = os.path.splitext(name)
        if mod.startswith("."):
            continue
        add_files(files, set(), root, name, args)
        add_configuration(configurations, distribution)


class TestResults(NamedTuple):
    exit_code: int
    files_checked: int


def test_third_party_distribution(distribution: str, args: TestConfig) -> TestResults:
    """Test the stubs of a third-party distribution.

    Return a tuple, where the first element indicates mypy's return code
    and the second element is the number of checked files.
    """

    files: list[str] = []
    configurations: list[MypyDistConf] = []
    seen_dists: set[str] = set()
    add_third_party_files(distribution, files, args, configurations, seen_dists)

    print(f"testing {distribution} ({len(files)} files)... ", end="")

    if not files:
        print_error("no files found")
        sys.exit(1)

    code = run_mypy(args, configurations, files)
    return TestResults(code, len(files))


def is_probably_stubs_folder(distribution: str, distribution_path: Path) -> bool:
    """Validate that `dist_path` is a folder containing stubs"""
    return distribution != ".mypy_cache" and distribution_path.is_dir()


def test_stdlib(code: int, args: TestConfig) -> TestResults:
    seen = {"__builtin__", "builtins", "typing"}  # Always ignore these.

    files: list[str] = []
    if args.major == 2:
        root = os.path.join("stdlib", "@python2")
        for name in os.listdir(root):
            mod, _ = os.path.splitext(name)
            if mod in seen or mod.startswith("."):
                continue
            add_files(files, seen, root, name, args)
    else:
        supported_versions = parse_versions(os.path.join("stdlib", "VERSIONS"))
        root = "stdlib"
        for name in os.listdir(root):
            if name == "@python2" or name == "VERSIONS" or name.startswith("."):
                continue
            mod, _ = os.path.splitext(name)
            if supported_versions[mod][0] <= (args.major, args.minor) <= supported_versions[mod][1]:
                add_files(files, seen, root, name, args)

    if files:
        print(f"Testing stdlib ({len(files)} files)...")
        print("Running mypy " + " ".join(get_mypy_flags(args, "/tmp/...", custom_typeshed=True)))
        this_code = run_mypy(args, [], files, custom_typeshed=True)
        code = max(code, this_code)

    return TestResults(code, len(files))


def test_third_party_stubs(code: int, args: TestConfig) -> TestResults:
    print("Testing third-party packages...")
    print("Running mypy " + " ".join(get_mypy_flags(args, "/tmp/...")))
    files_checked = 0

    for distribution in sorted(os.listdir("stubs")):
        distribution_path = Path("stubs", distribution)

        if not is_probably_stubs_folder(distribution, distribution_path):
            continue

        this_code, checked = test_third_party_distribution(distribution, args)
        code = max(code, this_code)
        files_checked += checked

    return TestResults(code, files_checked)


def test_the_test_scripts(code: int, args: TestConfig) -> TestResults:
    files_to_test = list(Path("tests").rglob("*.py"))
    if args.platform == "win32":
        files_to_test.remove(Path("tests/pytype_test.py"))
    num_test_files_to_test = len(files_to_test)
    flags = get_mypy_flags(args, None, strict=True, test_suite_run=True)
    print(f"Testing the test suite ({num_test_files_to_test} files)...")
    print("Running mypy " + " ".join(flags))
    if args.dry_run:
        this_code = 0
    else:
        this_code = run_mypy_as_subprocess("tests", flags)
    if not this_code:
        print_success_msg()
    code = max(code, this_code)
    return TestResults(code, num_test_files_to_test)


def test_scripts_directory(code: int, args: TestConfig) -> TestResults:
    files_to_test = list(Path("scripts").rglob("*.py"))
    num_test_files_to_test = len(files_to_test)
    flags = get_mypy_flags(args, None, strict=True, ignore_missing_imports=True)
    print(f"Testing the scripts directory ({num_test_files_to_test} files)...")
    print("Running mypy " + " ".join(flags))
    if args.dry_run:
        this_code = 0
    else:
        this_code = run_mypy_as_subprocess("scripts", flags)
    if not this_code:
        print_success_msg()
    code = max(code, this_code)
    return TestResults(code, num_test_files_to_test)


def test_the_test_cases(code: int, args: TestConfig) -> TestResults:
    test_case_files = list(map(str, Path("test_cases").rglob("*.py")))
    num_test_case_files = len(test_case_files)
    flags = get_mypy_flags(args, None, strict=True, custom_typeshed=True, enforce_error_codes=False)
    print(f"Running mypy on the test_cases directory ({num_test_case_files} files)...")
    print("Running mypy " + " ".join(flags))
    if args.dry_run:
        this_code = 0
    else:
        # --warn-unused-ignores doesn't work for files inside typeshed.
        # SO, to work around this, we copy the test_cases directory into a TemporaryDirectory.
        with tempfile.TemporaryDirectory() as td:
            shutil.copytree(Path("test_cases"), Path(td) / "test_cases")
            this_code = run_mypy_as_subprocess(td, flags)
    if not this_code:
        print_success_msg()
    code = max(code, this_code)
    return TestResults(code, num_test_case_files)


def test_typeshed(code: int, args: TestConfig) -> TestResults:
    print(f"*** Testing Python {args.major}.{args.minor} on {args.platform}")
    files_checked_this_version = 0
    if "stdlib" in args.directories:
        code, stdlib_files_checked = test_stdlib(code, args)
        files_checked_this_version += stdlib_files_checked
        print()

    if args.major == 2:
        return TestResults(code, files_checked_this_version)

    if "stubs" in args.directories:
        code, third_party_files_checked = test_third_party_stubs(code, args)
        files_checked_this_version += third_party_files_checked
        print()

    if args.minor >= 9:
        # Run mypy against our own test suite and the scripts directory
        #
        # Skip this on earlier Python versions,
        # as we're using new syntax and new functions in some test files
        if "tests" in args.directories:
            code, test_script_files_checked = test_the_test_scripts(code, args)
            files_checked_this_version += test_script_files_checked
            print()

        if "scripts" in args.directories:
            code, script_files_checked = test_scripts_directory(code, args)
            files_checked_this_version += script_files_checked
            print()

    if "test_cases" in args.directories:
        code, test_case_files_checked = test_the_test_cases(code, args)
        files_checked_this_version += test_case_files_checked
        print()

    return TestResults(code, files_checked_this_version)


def main() -> None:
    args = parser.parse_args(namespace=CommandLineArgs())
    versions = args.python_version or SUPPORTED_VERSIONS
    platforms = args.platform or [sys.platform]
    tested_directories = frozenset(args.dir) if args.dir else TYPESHED_DIRECTORIES
    code = 0
    total_files_checked = 0
    for (major, minor), platform in product(versions, platforms):
        config = TestConfig(
            verbose=args.verbose,
            dry_run=args.dry_run,
            exclude=args.exclude,
            major=major,
            minor=minor,
            directories=tested_directories,
            platform=platform,
            filter=args.filter,
        )
        code, files_checked_this_version = test_typeshed(code, args=config)
        total_files_checked += files_checked_this_version
    if code:
        print_error(f"--- exit status {code}, {total_files_checked} files checked ---")
        sys.exit(code)
    if not total_files_checked:
        print_error("--- nothing to do; exit 1 ---")
        sys.exit(1)
    print(colored(f"--- success, {total_files_checked} files checked ---", "green"))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_error("\n\n!!!\nTest aborted due to KeyboardInterrupt\n!!!")
        sys.exit(1)
