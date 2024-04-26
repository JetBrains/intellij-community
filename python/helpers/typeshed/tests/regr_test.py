#!/usr/bin/env python3
"""Run mypy on the test cases for the stdlib and third-party stubs."""

from __future__ import annotations

import argparse
import concurrent.futures
import os
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
from collections.abc import Callable
from contextlib import ExitStack, suppress
from dataclasses import dataclass
from enum import IntEnum
from functools import partial
from pathlib import Path
from typing_extensions import TypeAlias

from parse_metadata import get_recursive_requirements, read_metadata
from utils import (
    PYTHON_VERSION,
    PackageInfo,
    VenvInfo,
    colored,
    get_all_testcase_directories,
    get_mypy_req,
    make_venv,
    print_error,
    testcase_dir_from_package_name,
)

ReturnCode: TypeAlias = int

TEST_CASES = "test_cases"
VENV_DIR = ".venv"
TYPESHED = "typeshed"

SUPPORTED_PLATFORMS = ["linux", "darwin", "win32"]
SUPPORTED_VERSIONS = ["3.12", "3.11", "3.10", "3.9", "3.8", "3.7"]


def package_with_test_cases(package_name: str) -> PackageInfo:
    """Helper function for argument-parsing"""

    if package_name == "stdlib":
        return PackageInfo("stdlib", Path(TEST_CASES))
    test_case_dir = testcase_dir_from_package_name(package_name)
    if test_case_dir.is_dir():
        if not os.listdir(test_case_dir):
            raise argparse.ArgumentTypeError(f"{package_name!r} has a 'test_cases' directory but it is empty!")
        return PackageInfo(package_name, test_case_dir)
    raise argparse.ArgumentTypeError(f"No test cases found for {package_name!r}!")


class Verbosity(IntEnum):
    QUIET = 0
    NORMAL = 1
    VERBOSE = 2


parser = argparse.ArgumentParser(description="Script to run mypy against various test cases for typeshed's stubs")
parser.add_argument(
    "packages_to_test",
    type=package_with_test_cases,
    nargs="*",
    action="extend",
    help="Test only these packages (defaults to all typeshed stubs that have test cases)",
)
parser.add_argument(
    "--all",
    action="store_true",
    help=(
        'Run tests on all available platforms and versions (defaults to "False"). '
        "Note that this cannot be specified if --platform and/or --python-version are specified."
    ),
)
parser.add_argument(
    "--verbosity",
    choices=[member.name for member in Verbosity],
    default=Verbosity.NORMAL.name,
    help="Control how much output to print to the terminal",
)
parser.add_argument(
    "--platform",
    dest="platforms_to_test",
    choices=SUPPORTED_PLATFORMS,
    nargs="*",
    action="extend",
    help=(
        "Run mypy for certain OS platforms (defaults to sys.platform). "
        "Note that this cannot be specified if --all is also specified."
    ),
)
parser.add_argument(
    "-p",
    "--python-version",
    dest="versions_to_test",
    choices=SUPPORTED_VERSIONS,
    nargs="*",
    action="extend",
    help=(
        "Run mypy for certain Python versions (defaults to sys.version_info[:2]). "
        "Note that this cannot be specified if --all is also specified."
    ),
)

_PRINT_QUEUE: queue.SimpleQueue[str] = queue.SimpleQueue()


def verbose_log(msg: str) -> None:
    _PRINT_QUEUE.put(colored(msg, "blue"))


def setup_testcase_dir(package: PackageInfo, tempdir: Path, verbosity: Verbosity) -> None:
    if verbosity is verbosity.VERBOSE:
        verbose_log(f"{package.name}: Setting up testcase dir in {tempdir}")
    # --warn-unused-ignores doesn't work for files inside typeshed.
    # SO, to work around this, we copy the test_cases directory into a TemporaryDirectory,
    # and run the test cases inside of that.
    shutil.copytree(package.test_case_directory, tempdir / TEST_CASES)
    if package.is_stdlib:
        return

    # HACK: we want to run these test cases in an isolated environment --
    # we want mypy to see all stub packages listed in the "requires" field of METADATA.toml
    # (and all stub packages required by those stub packages, etc. etc.),
    # but none of the other stubs in typeshed.
    #
    # The best way of doing that without stopping --warn-unused-ignore from working
    # seems to be to create a "new typeshed" directory in a tempdir
    # that has only the required stubs copied over.
    new_typeshed = tempdir / TYPESHED
    new_typeshed.mkdir()
    shutil.copytree(Path("stdlib"), new_typeshed / "stdlib")
    requirements = get_recursive_requirements(package.name)
    # mypy refuses to consider a directory a "valid typeshed directory"
    # unless there's a stubs/mypy-extensions path inside it,
    # so add that to the list of stubs to copy over to the new directory
    for requirement in {package.name, *requirements.typeshed_pkgs, "mypy-extensions"}:
        shutil.copytree(Path("stubs", requirement), new_typeshed / "stubs" / requirement)

    if requirements.external_pkgs:
        pip_exe = make_venv(tempdir / VENV_DIR).pip_exe
        # Use --no-cache-dir to avoid issues with concurrent read/writes to the cache
        pip_command = [pip_exe, "install", get_mypy_req(), *requirements.external_pkgs, "--no-cache-dir"]
        if verbosity is Verbosity.VERBOSE:
            verbose_log(f"{package.name}: Setting up venv in {tempdir / VENV_DIR}. {pip_command=}\n")
        try:
            subprocess.run(pip_command, check=True, capture_output=True, text=True)
        except subprocess.CalledProcessError as e:
            _PRINT_QUEUE.put(f"{package.name}\n{e.stderr}")
            raise


def run_testcases(
    package: PackageInfo, version: str, platform: str, *, tempdir: Path, verbosity: Verbosity
) -> subprocess.CompletedProcess[str]:
    env_vars = dict(os.environ)
    new_test_case_dir = tempdir / TEST_CASES

    # "--enable-error-code ignore-without-code" is purposefully omitted.
    # See https://github.com/python/typeshed/pull/8083
    flags = [
        "--python-version",
        version,
        "--show-traceback",
        "--no-error-summary",
        "--platform",
        platform,
        "--strict",
        "--pretty",
    ]

    if package.is_stdlib:
        python_exe = sys.executable
        custom_typeshed = Path(__file__).parent.parent
        flags.append("--no-site-packages")
    else:
        custom_typeshed = tempdir / TYPESHED
        env_vars["MYPYPATH"] = os.pathsep.join(map(str, custom_typeshed.glob("stubs/*")))
        has_non_types_dependencies = (tempdir / VENV_DIR).exists()
        if has_non_types_dependencies:
            python_exe = VenvInfo.of_existing_venv(tempdir / VENV_DIR).python_exe
        else:
            python_exe = sys.executable
            flags.append("--no-site-packages")

    flags.extend(["--custom-typeshed-dir", str(custom_typeshed)])

    # If the test-case filename ends with -py39,
    # only run the test if --python-version was set to 3.9 or higher (for example)
    for path in new_test_case_dir.rglob("*.py"):
        if match := re.fullmatch(r".*-py3(\d{1,2})", path.stem):
            minor_version_required = int(match[1])
            assert f"3.{minor_version_required}" in SUPPORTED_VERSIONS
            python_minor_version = int(version.split(".")[1])
            if minor_version_required > python_minor_version:
                continue
        flags.append(str(path))

    mypy_command = [python_exe, "-m", "mypy"] + flags
    if verbosity is Verbosity.VERBOSE:
        description = f"{package.name}/{version}/{platform}"
        msg = f"{description}: {mypy_command=}\n"
        if "MYPYPATH" in env_vars:
            msg += f"{description}: {env_vars['MYPYPATH']=}"
        else:
            msg += f"{description}: MYPYPATH not set"
        msg += "\n"
        verbose_log(msg)
    return subprocess.run(mypy_command, capture_output=True, text=True, env=env_vars)


@dataclass(frozen=True)
class Result:
    code: int
    command_run: str
    stderr: str
    stdout: str
    test_case_dir: Path
    tempdir: Path

    def print_description(self, *, verbosity: Verbosity) -> None:
        if self.code:
            print(f"{self.command_run}:", end=" ")
            print_error("FAILURE\n")
            replacements = (str(self.tempdir / TEST_CASES), str(self.test_case_dir))
            if self.stderr:
                print_error(self.stderr, fix_path=replacements)
            if self.stdout:
                print_error(self.stdout, fix_path=replacements)


def test_testcase_directory(package: PackageInfo, version: str, platform: str, *, verbosity: Verbosity, tempdir: Path) -> Result:
    msg = f"mypy --platform {platform} --python-version {version} on the "
    msg += "standard library test cases" if package.is_stdlib else f"test cases for {package.name!r}"
    if verbosity > Verbosity.QUIET:
        _PRINT_QUEUE.put(f"Running {msg}...")

    proc_info = run_testcases(package=package, version=version, platform=platform, tempdir=tempdir, verbosity=verbosity)
    return Result(
        code=proc_info.returncode,
        command_run=msg,
        stderr=proc_info.stderr,
        stdout=proc_info.stdout,
        test_case_dir=package.test_case_directory,
        tempdir=tempdir,
    )


def print_queued_messages(ev: threading.Event) -> None:
    while not ev.is_set():
        with suppress(queue.Empty):
            print(_PRINT_QUEUE.get(timeout=0.5), flush=True)
    while True:
        try:
            msg = _PRINT_QUEUE.get_nowait()
        except queue.Empty:
            return
        else:
            print(msg, flush=True)


def concurrently_run_testcases(
    stack: ExitStack,
    testcase_directories: list[PackageInfo],
    verbosity: Verbosity,
    platforms_to_test: list[str],
    versions_to_test: list[str],
) -> list[Result]:
    packageinfo_to_tempdir = {
        package_info: Path(stack.enter_context(tempfile.TemporaryDirectory())) for package_info in testcase_directories
    }
    to_do: list[Callable[[], Result]] = []
    for testcase_dir, tempdir in packageinfo_to_tempdir.items():
        pkg = testcase_dir.name
        requires_python = None
        if not testcase_dir.is_stdlib:  # type: ignore[misc]  # mypy bug, already fixed on master
            requires_python = read_metadata(pkg).requires_python
            if not requires_python.contains(PYTHON_VERSION):
                msg = f"skipping {pkg!r} (requires Python {requires_python}; test is being run using Python {PYTHON_VERSION})"
                print(colored(msg, "yellow"))
                continue
        for version in versions_to_test:
            if not testcase_dir.is_stdlib:  # type: ignore[misc]  # mypy bug, already fixed on master
                assert requires_python is not None
                if not requires_python.contains(version):
                    msg = f"skipping {pkg!r} for target Python {version} (requires Python {requires_python})"
                    print(colored(msg, "yellow"))
                    continue
            to_do.extend(
                partial(test_testcase_directory, testcase_dir, version, platform, verbosity=verbosity, tempdir=tempdir)
                for platform in platforms_to_test
            )

    if not to_do:
        return []

    event = threading.Event()
    printer_thread = threading.Thread(target=print_queued_messages, args=(event,))
    printer_thread.start()

    with concurrent.futures.ThreadPoolExecutor(max_workers=os.cpu_count()) as executor:
        # Each temporary directory may be used by multiple processes concurrently during the next step;
        # must make sure that they're all setup correctly before starting the next step,
        # in order to avoid race conditions
        testcase_futures = [
            executor.submit(setup_testcase_dir, package, tempdir, verbosity)
            for package, tempdir in packageinfo_to_tempdir.items()
        ]
        concurrent.futures.wait(testcase_futures)

        mypy_futures = [executor.submit(task) for task in to_do]
        results = [future.result() for future in mypy_futures]

    event.set()
    printer_thread.join()
    return results


def main() -> ReturnCode:
    args = parser.parse_args()

    testcase_directories = args.packages_to_test or get_all_testcase_directories()
    verbosity = Verbosity[args.verbosity]
    if args.all:
        if args.platforms_to_test:
            parser.error("Cannot specify both --platform and --all")
        if args.versions_to_test:
            parser.error("Cannot specify both --python-version and --all")
        platforms_to_test, versions_to_test = SUPPORTED_PLATFORMS, SUPPORTED_VERSIONS
    else:
        platforms_to_test = args.platforms_to_test or [sys.platform]
        versions_to_test = args.versions_to_test or [PYTHON_VERSION]

    results: list[Result] | None = None

    with ExitStack() as stack:
        results = concurrently_run_testcases(stack, testcase_directories, verbosity, platforms_to_test, versions_to_test)

    assert results is not None
    if not results:
        print_error("All tests were skipped!")
        return 1

    print()

    for result in results:
        result.print_description(verbosity=verbosity)

    code = max(result.code for result in results)

    if code:
        print_error("Test completed with errors")
    else:
        print(colored("Test completed successfully!", "green"))

    return code


if __name__ == "__main__":
    try:
        code = main()
    except KeyboardInterrupt:
        print_error("Test aborted due to KeyboardInterrupt!")
        code = 1
    raise SystemExit(code)
