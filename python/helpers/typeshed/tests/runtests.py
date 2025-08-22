#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

from ts_utils.metadata import get_oldest_supported_python, read_metadata
from ts_utils.paths import TEST_CASES_DIR, test_cases_path
from ts_utils.utils import colored

_STRICTER_CONFIG_FILE = Path("pyrightconfig.stricter.json")
_TESTCASES_CONFIG_FILE = Path("pyrightconfig.testcases.json")
_NPX_ERROR_PATTERN = r"error (runn|find)ing npx"
_NPX_ERROR_MESSAGE = colored("\nSkipping Pyright tests: npx is not installed or can't be run!", "yellow")
_SUCCESS = colored("Success", "green")
_SKIPPED = colored("Skipped", "yellow")
_FAILED = colored("Failed", "red")


def _parse_jsonc(json_text: str) -> str:
    # strip comments from the file
    lines = [line for line in json_text.split("\n") if not line.strip().startswith("//")]
    # strip trailing commas from the file
    valid_json = re.sub(r",(\s*?[\}\]])", r"\1", "\n".join(lines))
    return valid_json


def _get_strict_params(stub_path: Path) -> list[str | Path]:
    data = json.loads(_parse_jsonc(_STRICTER_CONFIG_FILE.read_text(encoding="UTF-8")))
    lower_stub_path = stub_path.as_posix().lower()
    if any(lower_stub_path == stub.lower() for stub in data["exclude"]):
        return []
    return ["-p", _STRICTER_CONFIG_FILE]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--run-stubtest",
        action="store_true",
        help=(
            "Run stubtest for the selected package(s). Running stubtest may download and execute arbitrary code from PyPI: "
            "only use this option if you trust the package you are testing."
        ),
    )
    parser.add_argument(
        "--python-version",
        default=None,
        choices=("3.9", "3.10", "3.11", "3.12", "3.13", "3.14"),
        # We're using the oldest fully supported version because it's the most likely to produce errors
        # due to unsupported syntax, feature, or bug in a tool.
        help="Target Python version for the test (defaults to oldest supported Python version).",
    )
    parser.add_argument("path", help="Path of the stub to test in format <folder>/<stub>, from the root of the project.")
    args = parser.parse_args()
    path = Path(args.path)
    run_stubtest: bool = args.run_stubtest

    if len(path.parts) != 2:
        parser.error("'path' argument should be in format <folder>/<stub>.")
    folder, stub = path.parts
    if folder not in {"stdlib", "stubs"}:
        parser.error("Only the 'stdlib' and 'stubs' folders are supported.")
    if not path.exists():
        parser.error(f"{path=} does not exist.")

    if args.python_version:
        python_version: str = args.python_version
    elif folder in "stubs":
        python_version = read_metadata(stub).requires_python.version
    else:
        python_version = get_oldest_supported_python()

    stubtest_result: subprocess.CompletedProcess[bytes] | None = None

    print("\nRunning pre-commit...")
    pre_commit_result = subprocess.run(["pre-commit", "run", "--files", *path.rglob("*")], check=False)

    print("\nRunning check_typeshed_structure.py...")
    check_structure_result = subprocess.run([sys.executable, "tests/check_typeshed_structure.py"], check=False)

    strict_params = _get_strict_params(path)
    print(f"\nRunning Pyright ({'stricter' if strict_params else 'base' } configs) for Python {python_version}...")
    pyright_result = subprocess.run(
        [sys.executable, "tests/pyright_test.py", path, "--pythonversion", python_version, *strict_params],
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if re.match(_NPX_ERROR_PATTERN, pyright_result.stderr):
        print(_NPX_ERROR_MESSAGE)
        pyright_returncode = 0
        pyright_skipped = True
    else:
        print(pyright_result.stderr)
        pyright_returncode = pyright_result.returncode
        pyright_skipped = False

    print(f"\nRunning mypy for Python {python_version}...")
    mypy_result = subprocess.run([sys.executable, "tests/mypy_test.py", path, "--python-version", python_version], check=False)
    # If mypy failed, stubtest will fail without any helpful error
    if mypy_result.returncode == 0:
        if folder == "stdlib":
            print("\nRunning stubtest...")
            stubtest_result = subprocess.run([sys.executable, "tests/stubtest_stdlib.py", stub], check=False)
        else:
            if run_stubtest:
                print("\nRunning stubtest...")
                stubtest_result = subprocess.run([sys.executable, "tests/stubtest_third_party.py", stub], check=False)
            else:
                print(
                    colored(
                        f"\nSkipping stubtest for {stub!r}..."
                        + "\nNOTE: Running third-party stubtest involves downloading and executing arbitrary code from PyPI."
                        + f"\nOnly run stubtest if you trust the {stub!r} package.",
                        "yellow",
                    )
                )
    else:
        print(colored("\nSkipping stubtest since mypy failed.", "yellow"))

    cases_path = test_cases_path(stub if folder == "stubs" else "stdlib")
    if not cases_path.exists():
        # No test means they all ran successfully (0 out of 0). Not all 3rd-party stubs have regression tests.
        print(colored(f"\nRegression tests: No {TEST_CASES_DIR} folder for {stub!r}!", "green"))
        pyright_testcases_returncode = 0
        pyright_testcases_skipped = False
        regr_test_returncode = 0
    else:
        print(f"\nRunning Pyright regression tests for Python {python_version}...")
        command: list[str | Path] = [
            sys.executable,
            "tests/pyright_test.py",
            str(cases_path),
            "--pythonversion",
            python_version,
            "-p",
            _TESTCASES_CONFIG_FILE,
        ]
        pyright_testcases_result = subprocess.run(command, stderr=subprocess.PIPE, text=True, check=False)
        if re.match(_NPX_ERROR_PATTERN, pyright_testcases_result.stderr):
            print(_NPX_ERROR_MESSAGE)
            pyright_testcases_returncode = 0
            pyright_testcases_skipped = True
        else:
            print(pyright_result.stderr)
            pyright_testcases_returncode = pyright_testcases_result.returncode
            pyright_testcases_skipped = False

        print(f"\nRunning mypy regression tests for Python {python_version}...")
        regr_test_result = subprocess.run(
            [sys.executable, "tests/regr_test.py", "stdlib" if folder == "stdlib" else stub, "--python-version", python_version],
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        # No test means they all ran successfully (0 out of 0). Not all 3rd-party stubs have regression tests.
        if "No test cases found" in regr_test_result.stderr:
            regr_test_returncode = 0
            print(colored(f"\nNo test cases found for {stub!r}!", "green"))
        else:
            regr_test_returncode = regr_test_result.returncode
            print(regr_test_result.stderr)

    any_failure = any(
        [
            pre_commit_result.returncode,
            check_structure_result.returncode,
            pyright_returncode,
            mypy_result.returncode,
            getattr(stubtest_result, "returncode", 0),
            pyright_testcases_returncode,
            regr_test_returncode,
        ]
    )

    if any_failure:
        print(colored("\n\n--- TEST SUMMARY: One or more tests failed. See above for details. ---\n", "red"))
    else:
        print(colored("\n\n--- TEST SUMMARY: All tests passed! ---\n", "green"))
    if pre_commit_result.returncode == 0:
        print("pre-commit", _SUCCESS)
    else:
        print("pre-commit", _FAILED)
        print(
            """\
  Check the output of pre-commit for more details.
  This could mean that there's a lint failure on your code,
  but could also just mean that one of the pre-commit tools
  applied some autofixes. If the latter, you may want to check
  that the autofixes did sensible things."""
        )
    print("Check structure:", _SUCCESS if check_structure_result.returncode == 0 else _FAILED)
    if pyright_skipped:
        print("Pyright:", _SKIPPED)
    else:
        print("Pyright:", _SUCCESS if pyright_returncode == 0 else _FAILED)
    print("mypy:", _SUCCESS if mypy_result.returncode == 0 else _FAILED)
    if stubtest_result is None:
        print("stubtest:", _SKIPPED)
    else:
        print("stubtest:", _SUCCESS if stubtest_result.returncode == 0 else _FAILED)
    if pyright_testcases_skipped:
        print("Pyright regression tests:", _SKIPPED)
    else:
        print("Pyright regression tests:", _SUCCESS if pyright_testcases_returncode == 0 else _FAILED)
    print("mypy regression test:", _SUCCESS if regr_test_returncode == 0 else _FAILED)

    sys.exit(int(any_failure))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(colored("\nTests aborted due to KeyboardInterrupt!\n", "red"))
        sys.exit(1)
