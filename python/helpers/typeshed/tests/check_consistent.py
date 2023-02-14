#!/usr/bin/env python3

# For security (and simplicity) reasons, only a limited kind of files can be
# present in /stdlib and /stubs directories, see README for detail. Here we
# verify these constraints.

# In addition, for various reasons we need the contents of certain files to be
# duplicated in two places, for example stdlib/@python2/builtins.pyi and
# stdlib/@python2/__builtin__.pyi must be identical.  In the past we used
# symlinks but that doesn't always work on Windows, so now you must
# manually update both files, and this test verifies that they are
# identical.  The list below indicates which sets of files must match.
from __future__ import annotations

import filecmp
import os
import re
import sys

import tomli
from packaging.requirements import Requirement
from packaging.version import Version

consistent_files = [{"stdlib/@python2/builtins.pyi", "stdlib/@python2/__builtin__.pyi"}]
metadata_keys = {"version", "requires", "extra_description", "obsolete_since", "no_longer_updated", "tool"}
tool_keys = {"stubtest": {"skip", "apt_dependencies", "ignore_missing_stub"}}
allowed_files = {"README.md"}


def assert_stubs_only(directory: str) -> None:
    """Check that given directory contains only valid stub files."""
    top = directory.split(os.sep)[-1]
    assert top.isidentifier(), f"Bad directory name: {top}"
    for _, dirs, files in os.walk(directory):
        for file in files:
            if file in allowed_files:
                continue
            name, ext = os.path.splitext(file)
            assert name.isidentifier(), f"Files must be valid modules, got: {name}"
            assert ext == ".pyi", f"Only stub flies allowed. Got: {file} in {directory}"
        for subdir in dirs:
            assert subdir.isidentifier(), f"Directories must be valid packages, got: {subdir}"


def check_stdlib() -> None:
    for entry in os.listdir("stdlib"):
        if os.path.isfile(os.path.join("stdlib", entry)):
            name, ext = os.path.splitext(entry)
            if ext != ".pyi":
                assert entry == "VERSIONS", f"Unexpected file in stdlib root: {entry}"
            assert name.isidentifier(), "Bad file name in stdlib"
        else:
            if entry == "@python2":
                continue
            assert_stubs_only(os.path.join("stdlib", entry))
    for entry in os.listdir("stdlib/@python2"):
        if os.path.isfile(os.path.join("stdlib/@python2", entry)):
            name, ext = os.path.splitext(entry)
            assert name.isidentifier(), "Bad file name in stdlib"
            assert ext == ".pyi", "Unexpected file in stdlib/@python2 root"
        else:
            assert_stubs_only(os.path.join("stdlib/@python2", entry))


def check_stubs() -> None:
    for distribution in os.listdir("stubs"):
        assert not os.path.isfile(distribution), f"Only directories allowed in stubs, got {distribution}"
        for entry in os.listdir(os.path.join("stubs", distribution)):
            if os.path.isfile(os.path.join("stubs", distribution, entry)):
                name, ext = os.path.splitext(entry)
                if ext != ".pyi":
                    assert entry in {"METADATA.toml", "README", "README.md", "README.rst"}, entry
                else:
                    assert name.isidentifier(), f"Bad file name '{entry}' in stubs"
            else:
                if entry == "@tests":
                    continue
                assert_stubs_only(os.path.join("stubs", distribution, entry))


def check_same_files() -> None:
    files = [os.path.join(root, file) for root, dir, files in os.walk(".") for file in files]
    no_symlink = "You cannot use symlinks in typeshed, please copy {} to its link."
    for file in files:
        _, ext = os.path.splitext(file)
        if ext == ".pyi" and os.path.islink(file):
            raise ValueError(no_symlink.format(file))
    for file1, *others in consistent_files:
        f1 = os.path.join(os.getcwd(), file1)
        for file2 in others:
            f2 = os.path.join(os.getcwd(), file2)
            if not filecmp.cmp(f1, f2):
                raise ValueError(
                    "File {f1} does not match file {f2}. Please copy it to {f2}\n"
                    "Run either:\ncp {f1} {f2}\nOr:\ncp {f2} {f1}".format(f1=file1, f2=file2)
                )


_VERSIONS_RE = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_.]*): [23]\.\d{1,2}-(?:[23]\.\d{1,2})?$")


def check_versions() -> None:
    versions = set()
    with open("stdlib/VERSIONS") as f:
        data = f.read().splitlines()
    for line in data:
        line = line.split("#")[0].strip()
        if line == "":
            continue
        m = _VERSIONS_RE.match(line)
        if not m:
            raise AssertionError(f"Bad line in VERSIONS: {line}")
        module = m.group(1)
        assert module not in versions, f"Duplicate module {module} in VERSIONS"
        versions.add(module)
    modules = _find_stdlib_modules()
    # Sub-modules don't need to be listed in VERSIONS.
    extra = {m.split(".")[0] for m in modules} - versions
    assert not extra, f"Modules not in versions: {extra}"
    extra = versions - modules
    assert not extra, f"Versions not in modules: {extra}"


def _find_stdlib_modules() -> set[str]:
    modules = set()
    for path, _, files in os.walk("stdlib"):
        if "@python2" in path:
            continue
        for filename in files:
            base_module = ".".join(os.path.normpath(path).split(os.sep)[1:])
            if filename == "__init__.pyi":
                modules.add(base_module)
            elif filename.endswith(".pyi"):
                mod, _ = os.path.splitext(filename)
                modules.add(f"{base_module}.{mod}" if base_module else mod)
    return modules


def check_metadata() -> None:
    for distribution in os.listdir("stubs"):
        with open(os.path.join("stubs", distribution, "METADATA.toml")) as f:
            data = tomli.loads(f.read())
        assert "version" in data, f"Missing version for {distribution}"
        version = data["version"]
        msg = f"Unsupported version {repr(version)}"
        assert isinstance(version, str), msg
        # Check that the version parses
        Version(version.removesuffix(".*"))
        for key in data:
            assert key in metadata_keys, f"Unexpected key {key} for {distribution}"
        assert isinstance(data.get("requires", []), list), f"Invalid requires value for {distribution}"
        for dep in data.get("requires", []):
            assert isinstance(dep, str), f"Invalid requirement {repr(dep)} for {distribution}"
            for space in " \t\n":
                assert space not in dep, f"For consistency, requirement should not have whitespace: {dep}"
            # Check that the requirement parses
            Requirement(dep)

        assert set(data.get("tool", [])).issubset(tool_keys.keys()), f"Unrecognised tool for {distribution}"
        for tool, tk in tool_keys.items():
            for key in data.get("tool", {}).get(tool, {}):
                assert key in tk, f"Unrecognised {tool} key {key} for {distribution}"


if __name__ == "__main__":
    assert sys.version_info >= (3, 9), "Python 3.9+ is required to run this test"
    check_stdlib()
    check_versions()
    check_stubs()
    check_metadata()
    check_same_files()
