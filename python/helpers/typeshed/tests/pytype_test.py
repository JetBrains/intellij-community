#!/usr/bin/env python3
# Lack of pytype typing
# pyright: reportUnknownVariableType=false, reportUnknownMemberType=false, reportUnknownArgumentType=false, reportMissingTypeStubs=false
"""Test runner for typeshed.

Depends on pytype being installed.

If pytype is installed:
    1. For every pyi, do nothing if it is in pytype_exclude_list.txt.
    2. Otherwise, call 'pytype.io.parse_pyi'.
Option two will load the file and all the builtins, typeshed dependencies. This
will also discover incorrect usage of imported modules.
"""

from __future__ import annotations

import sys
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    assert sys.platform != "win32", "pytype isn't yet installed in CI, but wheels can be built on Windows"
    from _typeshed import StrPath
if sys.version_info >= (3, 13):
    print("pytype does not support Python 3.13+ yet.", file=sys.stderr)
    sys.exit(1)

import argparse
import importlib.metadata
import inspect
import os
import traceback
from collections.abc import Iterable, Sequence
from pathlib import Path

# pytype is not py.typed https://github.com/google/pytype/issues/1325
from pytype import config as pytype_config, load_pytd  # type: ignore[import]
from pytype.imports import typeshed  # type: ignore[import]

from ts_utils.metadata import read_dependencies
from ts_utils.paths import STDLIB_PATH, STUBS_PATH, TS_BASE_PATH
from ts_utils.utils import SupportedVersionsDict, parse_stdlib_versions_file, supported_versions_for_module

TYPESHED_SUBDIRS = [STDLIB_PATH.absolute(), STUBS_PATH.absolute()]
TYPESHED_HOME = "TYPESHED_HOME"
EXCLUDE_LIST = TS_BASE_PATH / "tests" / "pytype_exclude_list.txt"
_LOADERS: dict[str, tuple[pytype_config.Options, load_pytd.Loader]] = {}


def main() -> None:
    args = create_parser().parse_args()
    typeshed_location = Path(args.typeshed_location) or Path.cwd()
    check_subdirs_discoverable(TYPESHED_SUBDIRS)
    old_typeshed_home = os.environ.get(TYPESHED_HOME)
    os.environ[TYPESHED_HOME] = str(typeshed_location)
    files_to_test = determine_files_to_test(paths=[Path(file) for file in args.files] or TYPESHED_SUBDIRS)
    run_all_tests(files_to_test=files_to_test, print_stderr=args.print_stderr, dry_run=args.dry_run)
    if old_typeshed_home is None:
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


def run_pytype(*, filename: StrPath, python_version: str, missing_modules: Iterable[str]) -> str | None:
    """Run pytype, returning the stderr if any."""
    if python_version not in _LOADERS:
        options = pytype_config.Options.create("", parse_pyi=True, python_version=python_version)
        # For simplicity, pretends missing modules are part of the stdlib.
        missing_modules = tuple(str(STDLIB_PATH / m) for m in missing_modules)
        loader = load_pytd.create_loader(options, missing_modules)
        _LOADERS[python_version] = (options, loader)
    options, loader = _LOADERS[python_version]
    stderr: str | None
    try:
        with pytype_config.verbosity_from(options):
            ast = loader.load_file(_get_module_name(filename), filename)
            loader.finish_and_verify_ast(ast)
    except Exception:
        stderr = traceback.format_exc()
    else:
        stderr = None
    return stderr


def _get_relative(filename: StrPath) -> Path:
    filepath = Path(filename)
    for d in TYPESHED_SUBDIRS:
        try:
            return filepath.absolute().relative_to(d.parent)
        except ValueError:
            continue
    raise ValueError(f"{filepath} not relative to {TYPESHED_SUBDIRS}")


def _get_module_name(filename: StrPath) -> str:
    """Convert a filename {subdir}/m.n/module/foo to module.foo."""
    parts = _get_relative(filename).parts
    if parts[0] == "stdlib":
        module_parts = parts[1:]
    else:
        assert parts[0] == "stubs"
        module_parts = parts[2:]
    return ".".join(module_parts).replace(".pyi", "").replace(".__init__", "")


def check_subdirs_discoverable(subdir_paths: Iterable[Path]) -> None:
    for p in subdir_paths:
        if not p.is_dir():
            raise SystemExit(f"Cannot find typeshed subdir at {p} (specify parent dir via --typeshed-location)")


def determine_files_to_test(*, paths: Iterable[Path]) -> list[Path]:
    """Determine all files to test.

    Checks for files in the pytype exclude list and for the stdlib VERSIONS file.
    """
    filenames = find_stubs_in_paths(paths)
    ts = typeshed.Typeshed()
    exclude_list = set(ts.read_blacklist())
    stdlib_module_versions = parse_stdlib_versions_file()
    return [
        f
        for f in sorted(filenames)
        if _get_relative(f).as_posix() not in exclude_list and _is_supported_stdlib_version(stdlib_module_versions, f)
    ]


def find_stubs_in_paths(paths: Iterable[Path]) -> list[Path]:
    filenames: list[Path] = []
    for path in paths:
        if path.is_dir():
            for root, _, fns in os.walk(path):
                filenames.extend(Path(root, fn) for fn in fns if fn.endswith(".pyi"))
        else:
            filenames.append(path)
    return filenames


def _is_supported_stdlib_version(module_versions: SupportedVersionsDict, filename: StrPath) -> bool:
    parts = _get_relative(filename).parts
    if parts[0] != "stdlib":
        return True
    module_name = _get_module_name(filename)
    min_version, max_version = supported_versions_for_module(module_versions, module_name)
    return min_version <= sys.version_info <= max_version


def _get_pkgs_associated_with_requirement(req_name: str) -> list[str]:
    try:
        dist = importlib.metadata.distribution(req_name)
    except importlib.metadata.PackageNotFoundError:
        # The package wasn't installed, probably because an environment
        # marker excluded it.
        return []
    toplevel_txt_contents = dist.read_text("top_level.txt")
    if toplevel_txt_contents is None:
        if dist.files is None:
            raise RuntimeError(f"Can't read find the packages associated with requirement {req_name!r}")
        maybe_modules = [f.parts[0] if len(f.parts) > 1 else inspect.getmodulename(f) for f in dist.files]
        packages = [name for name in maybe_modules if name is not None and "." not in name]
    else:
        packages = toplevel_txt_contents.split()
    # https://peps.python.org/pep-0561/#stub-only-packages
    return sorted({package.removesuffix("-stubs") for package in packages})


def get_missing_modules(files_to_test: Iterable[Path]) -> Iterable[str]:
    """Get names of modules that should be treated as missing.

    Some typeshed stubs depend on dependencies outside of typeshed. Since pytype
    isn't able to read such dependencies, we instead declare them as "missing"
    modules, so that no errors are reported for them.

    Similarly, pytype cannot parse files on its exclude list, so we also treat
    those as missing.
    """
    stub_distributions = set[str]()
    for fi in files_to_test:
        parts = fi.parts
        try:
            idx = parts.index("stubs")
        except ValueError:
            continue
        stub_distributions.add(parts[idx + 1])

    missing_modules = {
        associated_package
        for distribution in stub_distributions
        for external_req in read_dependencies(distribution).external_pkgs
        for associated_package in _get_pkgs_associated_with_requirement(external_req.name)
    }

    with EXCLUDE_LIST.open() as f:
        for line in f:
            if not line.startswith("stubs/"):
                # Skips comments, empty lines, and stdlib files, which are in
                # the exclude list because pytype has its own version.
                continue
            _ts_subdir, _distribution, module_path = line.strip().split("/", 2)
            missing_modules.add(module_path.removesuffix(".pyi"))
    return missing_modules


def run_all_tests(*, files_to_test: Sequence[Path], print_stderr: bool, dry_run: bool) -> None:
    bad: list[tuple[StrPath, str, str]] = []
    errors = 0
    total_tests = len(files_to_test)
    missing_modules = get_missing_modules(files_to_test)
    python_version = f"{sys.version_info.major}.{sys.version_info.minor}"
    print("Testing files with pytype...")
    for i, file_to_test in enumerate(files_to_test):
        if dry_run:
            stderr = None
        else:
            stderr = run_pytype(filename=file_to_test, python_version=python_version, missing_modules=missing_modules)
        if stderr:
            if print_stderr:
                print(f"\n{stderr}")
            errors += 1
            stacktrace_final_line = stderr.rstrip().rsplit("\n", 1)[-1]
            bad.append((_get_relative(file_to_test), python_version, stacktrace_final_line))

        runs = i + 1
        if runs % 25 == 0:
            print(f"  {runs:3d}/{total_tests:d} with {errors:3d} errors")

    print(f"Ran pytype with {total_tests:d} pyis, got {errors:d} errors.")
    for f, v, err in bad:
        print(f"\n{f} ({v}): {err}")
    if errors:
        raise SystemExit("\nRun again with --print-stderr to get the full stacktrace.")


if __name__ == "__main__":
    main()
