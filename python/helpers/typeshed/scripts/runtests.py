#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

try:
    from termcolor import colored  # pyright: ignore[reportGeneralTypeIssues]
except ImportError:

    def colored(text: str, color: str | None = None, **kwargs: Any) -> str:  # type: ignore[misc]
        return text


_STRICTER_CONFIG_FILE = "pyrightconfig.stricter.json"
_TESTCASES_CONFIG_FILE = "pyrightconfig.testcases.json"
_TESTCASES = "test_cases"
_NPX_ERROR_PATTERN = r"error (runn|find)ing npx"
_NPX_ERROR_MESSAGE = colored("\nSkipping Pyright tests: npx is not installed or can't be run!", "yellow")
_SUCCESS = colored("Success", "green")
_SKIPPED = colored("Skipped", "yellow")
_FAILED = colored("Failed", "red")
# We're using the oldest fully supported version because it's the most likely to produce errors
# due to unsupported syntax, feature, or bug in a tool.
_PYTHON_VERSION = "3.8"


def _parse_jsonc(json_text: str) -> str:
    # strip comments from the file
    lines = [line for line in json_text.split("\n") if not line.strip().startswith("//")]
    # strip trailing commas from the file
    valid_json = re.sub(r",(\s*?[\}\]])", r"\1", "\n".join(lines))
    return valid_json


def _get_strict_params(stub_path: str) -> list[str]:
    with open(_STRICTER_CONFIG_FILE, encoding="UTF-8") as file:
        data = json.loads(_parse_jsonc(file.read()))
    lower_stub_path = stub_path.lower()
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
        default=_PYTHON_VERSION,
        choices=("3.8", "3.9", "3.10", "3.11", "3.12"),
        help="Target Python version for the test (default: %(default)s).",
    )
    parser.add_argument("path", help="Path of the stub to test in format <folder>/<stub>, from the root of the project.")
    args = parser.parse_args()
    path: str = args.path
    run_stubtest: bool = args.run_stubtest
    python_version: str = args.python_version

    path_tokens = Path(path).parts
    if len(path_tokens) != 2:
        parser.error("'path' argument should be in format <folder>/<stub>.")
    folder, stub = path_tokens
    if folder not in {"stdlib", "stubs"}:
        parser.error("Only the 'stdlib' and 'stubs' folders are supported.")
    if not os.path.exists(path):
        parser.error(rf"'path' {path} does not exist.")
    stubtest_result: subprocess.CompletedProcess[bytes] | None = None
    pytype_result: subprocess.CompletedProcess[bytes] | None = None

    # Run formatters first. Order matters.
    print("\nRunning Ruff...")
    subprocess.run([sys.executable, "-m", "ruff", path])
    print("\nRunning isort...")
    subprocess.run([sys.executable, "-m", "isort", path])
    print("\nRunning Black...")
    black_result = subprocess.run([sys.executable, "-m", "black", path])
    if black_result.returncode == 123:
        print("Could not run tests due to an internal error with Black. See above for details.", file=sys.stderr)
        sys.exit(black_result.returncode)

    print("\nRunning Flake8...")
    flake8_result = subprocess.run([sys.executable, "-m", "flake8", path])

    print("\nRunning check_consistent.py...")
    check_consistent_result = subprocess.run([sys.executable, "tests/check_consistent.py"])
    print("\nRunning check_new_syntax.py...")
    check_new_syntax_result = subprocess.run([sys.executable, "tests/check_new_syntax.py"])

    strict_params = _get_strict_params(path)
    print(f"\nRunning Pyright ({'stricter' if strict_params else 'base' } configs) for Python {python_version}...")
    pyright_result = subprocess.run(
        [sys.executable, "tests/pyright_test.py", path, "--pythonversion", python_version] + strict_params,
        stderr=subprocess.PIPE,
        text=True,
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
    mypy_result = subprocess.run([sys.executable, "tests/mypy_test.py", path, "--python-version", python_version])
    # If mypy failed, stubtest will fail without any helpful error
    if mypy_result.returncode == 0:
        if folder == "stdlib":
            print("\nRunning stubtest...")
            stubtest_result = subprocess.run([sys.executable, "tests/stubtest_stdlib.py", stub])
        else:
            if run_stubtest:
                print("\nRunning stubtest...")
                stubtest_result = subprocess.run([sys.executable, "tests/stubtest_third_party.py", stub])
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

    if sys.platform == "win32":
        print(colored("\nSkipping pytype on Windows. You can run the test with WSL.", "yellow"))
    else:
        print("\nRunning pytype...")
        pytype_result = subprocess.run([sys.executable, "tests/pytype_test.py", path])

    test_cases_path = Path(path) / "@tests" / _TESTCASES if folder == "stubs" else Path(_TESTCASES)
    if not test_cases_path.exists():
        # No test means they all ran successfully (0 out of 0). Not all 3rd-party stubs have regression tests.
        print(colored(f"\nRegression tests: No {_TESTCASES} folder for {stub!r}!", "green"))
        pyright_testcases_returncode = 0
        pyright_testcases_skipped = False
        regr_test_returncode = 0
    else:
        print(f"\nRunning Pyright regression tests for Python {python_version}...")
        command = [
            sys.executable,
            "tests/pyright_test.py",
            str(test_cases_path),
            "--pythonversion",
            python_version,
            "-p",
            _TESTCASES_CONFIG_FILE,
        ]
        pyright_testcases_result = subprocess.run(command, stderr=subprocess.PIPE, text=True)
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
            flake8_result.returncode,
            check_consistent_result.returncode,
            check_new_syntax_result.returncode,
            pyright_returncode,
            mypy_result.returncode,
            getattr(stubtest_result, "returncode", 0),
            getattr(pytype_result, "returncode", 0),
            pyright_testcases_returncode,
            regr_test_returncode,
        ]
    )

    if any_failure:
        print(colored("\n\n--- TEST SUMMARY: One or more tests failed. See above for details. ---\n", "red"))
    else:
        print(colored("\n\n--- TEST SUMMARY: All tests passed! ---\n", "green"))
    print("Flake8:", _SUCCESS if flake8_result.returncode == 0 else _FAILED)
    print("Check consistent:", _SUCCESS if check_consistent_result.returncode == 0 else _FAILED)
    print("Check new syntax:", _SUCCESS if check_new_syntax_result.returncode == 0 else _FAILED)
    if pyright_skipped:
        print("Pyright:", _SKIPPED)
    else:
        print("Pyright:", _SUCCESS if pyright_returncode == 0 else _FAILED)
    print("mypy:", _SUCCESS if mypy_result.returncode == 0 else _FAILED)
    if stubtest_result is None:
        print("stubtest:", _SKIPPED)
    else:
        print("stubtest:", _SUCCESS if stubtest_result.returncode == 0 else _FAILED)
    if not pytype_result:
        print("pytype:", _SKIPPED)
    else:
        print("pytype:", _SUCCESS if pytype_result.returncode == 0 else _FAILED)
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
