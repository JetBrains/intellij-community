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
from pathlib import Path

import tomli
import yaml
from packaging.requirements import Requirement
from packaging.specifiers import SpecifierSet
from packaging.version import Version
from utils import VERSIONS_RE, get_all_testcase_directories, strip_comments

consistent_files = [{"stdlib/@python2/builtins.pyi", "stdlib/@python2/__builtin__.pyi"}]
metadata_keys = {"version", "requires", "extra_description", "obsolete_since", "no_longer_updated", "tool"}
tool_keys = {"stubtest": {"skip", "apt_dependencies", "extras", "ignore_missing_stub"}}
extension_descriptions = {".pyi": "stub", ".py": ".py"}


def assert_consistent_filetypes(directory: Path, *, kind: str, allowed: set[str]) -> None:
    """Check that given directory contains only valid Python files of a certain kind."""
    allowed_paths = {Path(f) for f in allowed}
    contents = list(directory.iterdir())
    while contents:
        entry = contents.pop()
        if entry.relative_to(directory) in allowed_paths:
            # Note if a subdirectory is allowed, we will not check its contents
            continue
        if entry.is_file():
            assert entry.stem.isidentifier(), f'Files must be valid modules, got: "{entry}"'
            bad_filetype = f'Only {extension_descriptions[kind]!r} files allowed in the "{directory}" directory; got: {entry}'
            assert entry.suffix == kind, bad_filetype
        else:
            if entry == "@python2":
                continue
            assert entry.name.isidentifier(), f"Directories must be valid packages, got: {entry}"
            contents.extend(entry.iterdir())
    for entry in os.listdir("stdlib/@python2"):
        if os.path.isfile(os.path.join("stdlib/@python2", entry)):
            name, ext = os.path.splitext(entry)
            assert name.isidentifier(), "Bad file name in stdlib"
            assert ext == ".pyi", "Unexpected file in stdlib/@python2 root"
        else:
            assert_stubs_only(os.path.join("stdlib/@python2", entry))


def check_stdlib() -> None:
    assert_consistent_filetypes(Path("stdlib"), kind=".pyi", allowed={"_typeshed/README.md", "VERSIONS"})


def check_stubs() -> None:
    for dist in Path("stubs").iterdir():
        assert dist.is_dir(), f"Only directories allowed in stubs, got {dist}"

        valid_dist_name = "^([A-Z0-9]|[A-Z0-9][A-Z0-9._-]*[A-Z0-9])$"  # courtesy of PEP 426
        assert re.fullmatch(
            valid_dist_name, dist.name, re.IGNORECASE
        ), f"Directory name must be a valid distribution name: {dist}"
        assert not dist.name.startswith("types-"), f"Directory name not allowed to start with 'types-': {dist}"

        allowed = {"METADATA.toml", "README", "README.md", "README.rst", "@tests"}
        assert_consistent_filetypes(dist, kind=".pyi", allowed=allowed)


def check_test_cases() -> None:
    for package_name, testcase_dir in get_all_testcase_directories():
        assert_consistent_filetypes(testcase_dir, kind=".py", allowed={"README.md"})
        bad_test_case_filename = 'Files in a `test_cases` directory must have names starting with "check_"; got "{}"'
        for file in testcase_dir.rglob("*.py"):
            assert file.stem.startswith("check_"), bad_test_case_filename.format(file)
            if package_name != "stdlib":
                with open(file) as f:
                    lines = {line.strip() for line in f}
                pyright_setting_not_enabled_msg = (
                    f'Third-party test-case file "{file}" must have '
                    f'"# pyright: reportUnnecessaryTypeIgnoreComment=true" '
                    f"at the top of the file"
                )
                has_pyright_setting_enabled = "# pyright: reportUnnecessaryTypeIgnoreComment=true" in lines
                assert has_pyright_setting_enabled, pyright_setting_not_enabled_msg


def check_no_symlinks() -> None:
    files = [os.path.join(root, file) for root, _, files in os.walk(".") for file in files]
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


def check_versions() -> None:
    versions = set()
    with open("stdlib/VERSIONS") as f:
        data = f.read().splitlines()
    for line in data:
        line = strip_comments(line)
        if line == "":
            continue
        m = VERSIONS_RE.match(line)
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


def get_txt_requirements() -> dict[str, SpecifierSet]:
    with open("requirements-tests.txt") as requirements_file:
        stripped_lines = map(strip_comments, requirements_file)
        requirements = map(Requirement, filter(None, stripped_lines))
        return {requirement.name: requirement.specifier for requirement in requirements}


def get_precommit_requirements() -> dict[str, SpecifierSet]:
    with open(".pre-commit-config.yaml") as precommit_file:
        precommit = precommit_file.read()
    yam = yaml.load(precommit, Loader=yaml.Loader)
    precommit_requirements = {}
    for repo in yam["repos"]:
        hook = repo["hooks"][0]
        package_name, package_rev = hook["id"], repo["rev"]
        package_specifier = SpecifierSet(f"=={package_rev.removeprefix('v')}")
        precommit_requirements[package_name] = package_specifier
        for additional_req in hook.get("additional_dependencies", []):
            req = Requirement(additional_req)
            precommit_requirements[req.name] = req.specifier
    return precommit_requirements


def check_requirements() -> None:
    requirements_txt_requirements = get_txt_requirements()
    precommit_requirements = get_precommit_requirements()
    no_txt_entry_msg = "All pre-commit requirements must also be listed in `requirements-tests.txt` (missing {requirement!r})"
    for requirement, specifier in precommit_requirements.items():
        assert requirement in requirements_txt_requirements, no_txt_entry_msg.format(requirement)
        specifier_mismatch = (
            f'Specifier "{specifier}" for {requirement!r} in `.pre-commit-config.yaml` '
            f'does not match specifier "{requirements_txt_requirements[requirement]}" in `requirements-tests.txt`'
        )
        assert specifier == requirements_txt_requirements[requirement], specifier_mismatch


if __name__ == "__main__":
    assert sys.version_info >= (3, 9), "Python 3.9+ is required to run this test"
    check_stdlib()
    check_versions()
    check_stubs()
    check_metadata()
    check_no_symlinks()
    check_test_cases()
    check_requirements()
