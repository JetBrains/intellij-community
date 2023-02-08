#!/usr/bin/env python3
"""Test runner for typeshed.

Depends on mypy being installed.

Approach:

1. Parse sys.argv
2. Compute appropriate arguments for mypy
3. Pass those arguments to mypy.api.run()
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import TYPE_CHECKING, NamedTuple

if TYPE_CHECKING:
    from _typeshed import StrPath

from typing_extensions import TypeAlias

import tomli

parser = argparse.ArgumentParser(description="Test runner for typeshed. Patterns are unanchored regexps on the full path.")
parser.add_argument("-v", "--verbose", action="count", default=0, help="More output")
parser.add_argument("-n", "--dry-run", action="store_true", help="Don't actually run mypy")
parser.add_argument("-x", "--exclude", type=str, nargs="*", help="Exclude pattern")
parser.add_argument("-p", "--python-version", type=str, nargs="*", help="These versions only (major[.minor])")
parser.add_argument("--platform", help="Run mypy for a certain OS platform (defaults to sys.platform)")
parser.add_argument("filter", type=str, nargs="*", help="Include pattern (default all)")


def log(args: argparse.Namespace, *varargs: object) -> None:
    if args.verbose >= 2:
        print(*varargs)


def match(fn: str, args: argparse.Namespace) -> bool:
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
MinVersion: TypeAlias = tuple[int, int]
MaxVersion: TypeAlias = tuple[int, int]


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


def add_files(files: list[str], seen: set[str], root: str, name: str, args: argparse.Namespace) -> None:
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
        assert isinstance(mypy_section, dict), "{} should be a section".format(section_name)
        module_name = mypy_section.get("module_name")

        assert module_name is not None, "{} should have a module_name key".format(section_name)
        assert isinstance(module_name, str), "{} should be a key-value pair".format(section_name)

        values = mypy_section.get("values")
        assert values is not None, "{} should have a values section".format(section_name)
        assert isinstance(values, dict), "values should be a section"

        configurations.append(MypyDistConf(module_name, values.copy()))


def run_mypy(
    args: argparse.Namespace,
    configurations: list[MypyDistConf],
    major: int,
    minor: int,
    files: list[str],
    *,
    custom_typeshed: bool = False,
) -> int:
    try:
        from mypy.api import run as mypy_run
    except ImportError:
        print("Cannot import mypy. Did you install it?")
        sys.exit(1)

    with tempfile.NamedTemporaryFile("w+") as temp:
        temp.write("[mypy]\n")
        for dist_conf in configurations:
            temp.write("[mypy-%s]\n" % dist_conf.module_name)
            for k, v in dist_conf.values.items():
                temp.write("{} = {}\n".format(k, v))
        temp.flush()

        flags = get_mypy_flags(args, major, minor, temp.name, custom_typeshed=custom_typeshed)
        mypy_args = [*flags, *files]
        if args.verbose:
            print("running mypy", " ".join(mypy_args))
        if args.dry_run:
            exit_code = 0
        else:
            stdout, stderr, exit_code = mypy_run(mypy_args)
            print(stdout, end="")
            print(stderr, file=sys.stderr, end="")
        return exit_code


def get_mypy_flags(
    args: argparse.Namespace,
    major: int,
    minor: int,
    temp_name: str | None,
    *,
    custom_typeshed: bool = False,
    strict: bool = False,
    test_suite_run: bool = False,
) -> list[str]:
    flags = [
        "--python-version",
        "%d.%d" % (major, minor),
        "--show-traceback",
        "--no-implicit-optional",
        "--disallow-untyped-decorators",
        "--disallow-any-generics",
        "--warn-incomplete-stub",
        "--show-error-codes",
        "--no-error-summary",
        "--enable-error-code",
        "ignore-without-code",
        "--strict-equality",
    ]
    if temp_name is not None:
        flags.extend(["--config-file", temp_name])
    if custom_typeshed:
        # Setting custom typeshed dir prevents mypy from falling back to its bundled
        # typeshed in case of stub deletions
        flags.extend(["--custom-typeshed-dir", os.path.dirname(os.path.dirname(__file__))])
    if args.platform:
        flags.extend(["--platform", args.platform])
    if strict:
        flags.append("--strict")
    if test_suite_run:
        if sys.platform == "win32" or args.platform == "win32":
            flags.extend(["--exclude", "tests/pytype_test.py"])
    else:
        flags.append("--no-site-packages")
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
    distribution: str,
    major: int,
    files: list[str],
    args: argparse.Namespace,
    configurations: list[MypyDistConf],
    seen_dists: set[str],
) -> None:
    if distribution in seen_dists:
        return
    seen_dists.add(distribution)

    dependencies = read_dependencies(distribution)
    for dependency in dependencies:
        add_third_party_files(dependency, major, files, args, configurations, seen_dists)

    root = os.path.join("stubs", distribution)
    for name in os.listdir(root):
        mod, _ = os.path.splitext(name)
        if mod.startswith("."):
            continue
        add_files(files, set(), root, name, args)
        add_configuration(configurations, distribution)


def test_third_party_distribution(distribution: str, major: int, minor: int, args: argparse.Namespace) -> tuple[int, int]:
    """Test the stubs of a third-party distribution.

    Return a tuple, where the first element indicates mypy's return code
    and the second element is the number of checked files.
    """

    files: list[str] = []
    configurations: list[MypyDistConf] = []
    seen_dists: set[str] = set()
    add_third_party_files(distribution, major, files, args, configurations, seen_dists)

    print(f"testing {distribution} ({len(files)} files)...")

    if not files:
        print("--- no files found ---")
        sys.exit(1)

    code = run_mypy(args, configurations, major, minor, files)
    return code, len(files)


def is_probably_stubs_folder(distribution: str, distribution_path: Path) -> bool:
    """Validate that `dist_path` is a folder containing stubs"""
    return distribution != ".mypy_cache" and distribution_path.is_dir()


class TestResults(NamedTuple):
    exit_code: int
    files_checked: int


def test_stdlib(code: int, major: int, minor: int, args: argparse.Namespace) -> TestResults:
    seen = {"__builtin__", "builtins", "typing"}  # Always ignore these.

    files: list[str] = []
    if major == 2:
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
            if supported_versions[mod][0] <= (major, minor) <= supported_versions[mod][1]:
                add_files(files, seen, root, name, args)

    if files:
        print(f"Testing stdlib ({len(files)} files)...")
        print("Running mypy " + " ".join(get_mypy_flags(args, major, minor, "/tmp/...", custom_typeshed=True)))
        this_code = run_mypy(args, [], major, minor, files, custom_typeshed=True)
        code = max(code, this_code)

    return TestResults(code, len(files))


def test_third_party_stubs(code: int, major: int, minor: int, args: argparse.Namespace) -> TestResults:
    print("Testing third-party packages...")
    print("Running mypy " + " ".join(get_mypy_flags(args, major, minor, "/tmp/...")))
    files_checked = 0

    for distribution in sorted(os.listdir("stubs")):
        if distribution == "SQLAlchemy":
            continue  # Crashes

        distribution_path = Path("stubs", distribution)

        if not is_probably_stubs_folder(distribution, distribution_path):
            continue

        this_code, checked = test_third_party_distribution(distribution, major, minor, args)
        code = max(code, this_code)
        files_checked += checked

    return TestResults(code, files_checked)


def test_the_test_scripts(code: int, major: int, minor: int, args: argparse.Namespace) -> TestResults:
    files_to_test = list(Path("tests").rglob("*.py"))
    if sys.platform == "win32":
        files_to_test.remove(Path("tests/pytype_test.py"))
    num_test_files_to_test = len(files_to_test)
    flags = get_mypy_flags(args, major, minor, None, strict=True, test_suite_run=True)
    print(f"Testing the test suite ({num_test_files_to_test} files)...")
    print("Running mypy " + " ".join(flags))
    if args.dry_run:
        this_code = 0
    else:
        this_code = subprocess.run([sys.executable, "-m", "mypy", "tests", *flags]).returncode
    code = max(code, this_code)
    return TestResults(code, num_test_files_to_test)


def test_the_test_cases(code: int, major: int, minor: int, args: argparse.Namespace) -> TestResults:
    test_case_files = list(map(str, Path("test_cases").rglob("*.py")))
    num_test_case_files = len(test_case_files)
    flags = get_mypy_flags(args, major, minor, None, strict=True, custom_typeshed=True)
    print(f"Running mypy on the test_cases directory ({num_test_case_files} files)...")
    print("Running mypy " + " ".join(flags))
    if args.dry_run:
        this_code = 0
    else:
        this_code = subprocess.run([sys.executable, "-m", "mypy", "test_cases", *flags]).returncode
    code = max(code, this_code)
    return TestResults(code, num_test_case_files)


def test_typeshed(code: int, major: int, minor: int, args: argparse.Namespace) -> TestResults:
    print(f"*** Testing Python {major}.{minor}")
    files_checked_this_version = 0
    code, stdlib_files_checked = test_stdlib(code, major, minor, args)
    files_checked_this_version += stdlib_files_checked
    print()

    if major == 2:
        return TestResults(code, files_checked_this_version)

    code, third_party_files_checked = test_third_party_stubs(code, major, minor, args)
    files_checked_this_version += third_party_files_checked
    print()

    if minor >= 9:
        # Run mypy against our own test suite
        #
        # Skip this on earlier Python versions,
        # as we're using new syntax and new functions in some test files
        code, test_script_files_checked = test_the_test_scripts(code, major, minor, args)
        files_checked_this_version += test_script_files_checked
        print()

    code, test_case_files_checked = test_the_test_cases(code, major, minor, args)
    files_checked_this_version += test_case_files_checked
    print()

    return TestResults(code, files_checked_this_version)


def main() -> None:
    args = parser.parse_args()

    versions = [(3, 11), (3, 10), (3, 9), (3, 8), (3, 7), (3, 6), (2, 7)]
    if args.python_version:
        versions = [v for v in versions if any(("%d.%d" % v).startswith(av) for av in args.python_version)]
        if not versions:
            print("--- no versions selected ---")
            sys.exit(1)

    code = 0
    total_files_checked = 0
    for major, minor in versions:
        code, files_checked_this_version = test_typeshed(code, major, minor, args)
        total_files_checked += files_checked_this_version
    if code:
        print(f"--- exit status {code}, {total_files_checked} files checked ---")
        sys.exit(code)
    if not total_files_checked:
        print("--- nothing to do; exit 1 ---")
        sys.exit(1)
    print(f"--- success, {total_files_checked} files checked ---")


if __name__ == "__main__":
    main()
