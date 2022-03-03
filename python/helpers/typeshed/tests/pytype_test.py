#!/usr/bin/env python3
"""Test runner for typeshed.

Depends on pytype being installed.

If pytype is installed:
    1. For every pyi, do nothing if it is in pytype_exclude_list.txt or is
       Python 2-only.
    2. Otherwise, call 'pytype.io.parse_pyi'.
Option two will load the file and all the builtins, typeshed dependencies. This
will also discover incorrect usage of imported modules.
"""

import argparse
import os
import sys
import traceback
from typing import List, Optional, Sequence

from pytype import config as pytype_config, load_pytd
from pytype.pytd import typeshed

TYPESHED_SUBDIRS = ["stdlib", "stubs"]


TYPESHED_HOME = "TYPESHED_HOME"
UNSET = object()  # marker for tracking the TYPESHED_HOME environment variable

_LOADERS = {}


def main() -> None:
    args = create_parser().parse_args()
    typeshed_location = args.typeshed_location or os.getcwd()
    subdir_paths = [os.path.join(typeshed_location, d) for d in TYPESHED_SUBDIRS]
    check_subdirs_discoverable(subdir_paths)
    old_typeshed_home = os.environ.get(TYPESHED_HOME, UNSET)
    os.environ[TYPESHED_HOME] = typeshed_location
    files_to_test = determine_files_to_test(typeshed_location=typeshed_location, paths=args.files or subdir_paths)
    run_all_tests(
        files_to_test=files_to_test, typeshed_location=typeshed_location, print_stderr=args.print_stderr, dry_run=args.dry_run
    )
    if old_typeshed_home is UNSET:
        del os.environ[TYPESHED_HOME]
    else:
        os.environ[TYPESHED_HOME] = old_typeshed_home


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Pytype/typeshed tests.")
    parser.add_argument("-n", "--dry-run", action="store_true", default=False, help="Don't actually run tests")
    # Default to '' so that symlinking typeshed subdirs in cwd will work.
    parser.add_argument("--typeshed-location", type=str, default="", help="Path to typeshed installation.")
    # Set to true to print a stack trace every time an exception is thrown.
    parser.add_argument(
        "--print-stderr", action="store_true", default=False, help="Print stderr every time an error is encountered."
    )
    parser.add_argument(
        "files", metavar="FILE", type=str, nargs="*", help="Files or directories to check. (Default: Check all files.)"
    )
    return parser


def run_pytype(*, filename: str, python_version: str, typeshed_location: str) -> Optional[str]:
    """Runs pytype, returning the stderr if any."""
    if python_version not in _LOADERS:
        options = pytype_config.Options.create("", parse_pyi=True, python_version=python_version)
        loader = load_pytd.create_loader(options)
        _LOADERS[python_version] = (options, loader)
    options, loader = _LOADERS[python_version]
    try:
        with pytype_config.verbosity_from(options):
            ast = loader.load_file(_get_module_name(filename), filename)
            loader.finish_and_verify_ast(ast)
    except Exception:
        stderr = traceback.format_exc()
    else:
        stderr = None
    return stderr


def _get_relative(filename: str) -> str:
    top = 0
    for d in TYPESHED_SUBDIRS:
        try:
            top = filename.index(d)
        except ValueError:
            continue
        else:
            break
    return filename[top:]


def _get_module_name(filename: str) -> str:
    """Converts a filename {subdir}/m.n/module/foo to module.foo."""
    parts = _get_relative(filename).split(os.path.sep)
    if "@python2" in parts:
        module_parts = parts[parts.index("@python2") + 1 :]
    elif parts[0] == "stdlib":
        module_parts = parts[1:]
    else:
        assert parts[0] == "stubs"
        module_parts = parts[2:]
    return ".".join(module_parts).replace(".pyi", "").replace(".__init__", "")


def _is_version(path: str, version: str) -> bool:
    return any("{}{}{}".format(d, os.path.sep, version) in path for d in TYPESHED_SUBDIRS)


def check_subdirs_discoverable(subdir_paths: List[str]) -> None:
    for p in subdir_paths:
        if not os.path.isdir(p):
            raise SystemExit("Cannot find typeshed subdir at {} (specify parent dir via --typeshed-location)".format(p))


def determine_files_to_test(*, typeshed_location: str, paths: Sequence[str]) -> List[str]:
    """Determine all files to test, checking if it's in the exclude list and which Python versions to use.

    Returns a list of pairs of the file path and Python version as an int."""
    filenames = find_stubs_in_paths(paths)
    ts = typeshed.Typeshed()
    skipped = set(ts.read_blacklist())
    files = []
    for f in sorted(filenames):
        rel = _get_relative(f)
        if rel in skipped or "@python2" in f:
            continue
        files.append(f)
    return files


def find_stubs_in_paths(paths: Sequence[str]) -> List[str]:
    filenames = []
    for path in paths:
        if os.path.isdir(path):
            for root, _, fns in os.walk(path):
                filenames.extend(os.path.join(root, fn) for fn in fns if fn.endswith(".pyi"))
        else:
            filenames.append(path)
    return filenames


def run_all_tests(*, files_to_test: Sequence[str], typeshed_location: str, print_stderr: bool, dry_run: bool) -> None:
    bad = []
    errors = 0
    total_tests = len(files_to_test)
    print("Testing files with pytype...")
    for i, f in enumerate(files_to_test):
        python_version = "{0.major}.{0.minor}".format(sys.version_info)
        stderr = (
            run_pytype(filename=f, python_version=python_version, typeshed_location=typeshed_location) if not dry_run else None
        )
        if stderr:
            if print_stderr:
                print(stderr)
            errors += 1
            stacktrace_final_line = stderr.rstrip().rsplit("\n", 1)[-1]
            bad.append((_get_relative(f), python_version, stacktrace_final_line))

        runs = i + 1
        if runs % 25 == 0:
            print("  {:3d}/{:d} with {:3d} errors".format(runs, total_tests, errors))

    print("Ran pytype with {:d} pyis, got {:d} errors.".format(total_tests, errors))
    for f, v, err in bad:
        print("{} ({}): {}".format(f, v, err))
    if errors:
        raise SystemExit("\nRun again with --print-stderr to get the full stacktrace.")


if __name__ == "__main__":
    main()
