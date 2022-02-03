#!/usr/bin/env python3

"""Script to generate unannotated baseline stubs using stubgen.

Basic usage:
$ python3 scripts/create_baseline_stubs.py <project on PyPI>

Run with -h for more help.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
from typing import Optional, Tuple

PYRIGHT_CONFIG = "pyrightconfig.stricter.json"


def search_pip_freeze_output(project: str, output: str) -> Optional[Tuple[str, str]]:
    # Look for lines such as "typed-ast==1.4.2".  '-' matches '_' and
    # '_' matches '-' in project name, so that "typed_ast" matches
    # "typed-ast", and vice versa.
    regex = "^(" + re.sub(r"[-_]", "[-_]", project) + ")==(.*)"
    m = re.search(regex, output, flags=re.IGNORECASE | re.MULTILINE)
    if not m:
        return None
    return m.group(1), m.group(2)


def get_installed_package_info(project: str) -> Optional[Tuple[str, str]]:
    """Find package information from pip freeze output.

    Match project name somewhat fuzzily (case sensitive; '-' matches '_', and
    vice versa).

    Return (normalized project name, installed version) if successful.
    """
    r = subprocess.run(["pip", "freeze"], capture_output=True, text=True, check=True)
    return search_pip_freeze_output(project, r.stdout)


def run_stubgen(package: str) -> None:
    print(f"Running stubgen: stubgen -p {package}")
    subprocess.run(["python", "-m", "mypy.stubgen", "-p", package], check=True)


def copy_stubs(src_base_dir: str, package: str, stub_dir: str) -> None:
    """Copy generated stubs to the target directory under stub_dir/."""
    print(f"Copying stubs to {stub_dir}")
    if not os.path.isdir(stub_dir):
        os.mkdir(stub_dir)
    src_dir = os.path.join(src_base_dir, package)
    if os.path.isdir(src_dir):
        shutil.copytree(src_dir, os.path.join(stub_dir, package))
    else:
        src_file = os.path.join("out", package + ".pyi")
        if not os.path.isfile(src_file):
            sys.exit("Error: Cannot find generated stubs")
        shutil.copy(src_file, stub_dir)


def run_black(stub_dir: str) -> None:
    print(f"Running black: black {stub_dir}")
    subprocess.run(["black", stub_dir])


def run_isort(stub_dir: str) -> None:
    print(f"Running isort: isort {stub_dir}")
    subprocess.run(["python3", "-m", "isort", stub_dir])


def create_metadata(stub_dir: str, version: str) -> None:
    """Create a METADATA.toml file."""
    m = re.match(r"[0-9]+.[0-9]+", version)
    if m is None:
        sys.exit(f"Error: Cannot parse version number: {version}")
    fnam = os.path.join(stub_dir, "METADATA.toml")
    version = m.group(0)
    assert not os.path.exists(fnam)
    print(f"Writing {fnam}")
    with open(fnam, "w") as f:
        f.write(f'version = "{version}.*"\n')


def add_pyright_exclusion(stub_dir: str) -> None:
    """Exclude stub_dir from strict pyright checks."""
    with open(PYRIGHT_CONFIG) as f:
        lines = f.readlines()
    i = 0
    while i < len(lines) and not lines[i].strip().startswith('"exclude": ['):
        i += 1
    assert i < len(lines), f"Error parsing {PYRIGHT_CONFIG}"
    while not lines[i].strip().startswith("]"):
        i += 1
    line_to_add = f'        "{stub_dir}",'
    initial = i - 1
    while lines[i].lower() > line_to_add.lower():
        i -= 1
    if lines[i + 1].strip().rstrip(",") == line_to_add.strip().rstrip(","):
        print(f"{PYRIGHT_CONFIG} already up-to-date")
        return
    if i == initial:
        # Special case: when adding to the end of the list, commas need tweaking
        line_to_add = line_to_add.rstrip(",")
        lines[i] = lines[i].rstrip() + ",\n"
    lines.insert(i + 1, line_to_add + "\n")
    print(f"Updating {PYRIGHT_CONFIG}")
    with open(PYRIGHT_CONFIG, "w") as f:
        f.writelines(lines)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="""Generate baseline stubs automatically for an installed pip package
                       using stubgen. Also run black and isort. If the name of
                       the project is different from the runtime Python package name, you must
                       also use --package (example: --package yaml PyYAML)."""
    )
    parser.add_argument("project", help="name of PyPI project for which to generate stubs under stubs/")
    parser.add_argument("--package", help="generate stubs for this Python package (defaults to project)")
    args = parser.parse_args()
    project = args.project
    package = args.package

    if not re.match(r"[a-zA-Z0-9-_.]+$", project):
        sys.exit(f"Invalid character in project name: {project!r}")

    if not package:
        package = project  # TODO: infer from installed files

    if not os.path.isdir("stubs") or not os.path.isdir("stdlib"):
        sys.exit("Error: Current working directory must be the root of typeshed repository")

    # Get normalized project name and version of installed package.
    info = get_installed_package_info(project)
    if info is None:
        print(f'Error: "{project}" is not installed', file=sys.stderr)
        print("", file=sys.stderr)
        print(f'Suggestion: Run "python3 -m pip install {project}" and try again', file=sys.stderr)
        sys.exit(1)
    project, version = info

    stub_dir = os.path.join("stubs", project)
    if os.path.exists(stub_dir):
        sys.exit(f"Error: {stub_dir} already exists (delete it first)")

    run_stubgen(package)

    # Stubs were generated under out/. Copy them to stubs/.
    copy_stubs("out", package, stub_dir)

    run_isort(stub_dir)
    run_black(stub_dir)

    create_metadata(stub_dir, version)

    # Since the generated stubs won't have many type annotations, we
    # have to exclude them from strict pyright checks.
    add_pyright_exclusion(stub_dir)

    print("\nDone!\n\nSuggested next steps:")
    print(f" 1. Manually review the generated stubs in {stub_dir}")
    print(f' 2. Run "MYPYPATH={stub_dir} python3 -m mypy.stubtest {package}" to check the stubs against runtime')
    print(f' 3. Run "mypy {stub_dir}" to check for errors')
    print(f' 4. Run "black {stub_dir}" and "isort {stub_dir}" (if you\'ve made code changes)')
    print(f' 5. Run "flake8 {stub_dir}" to check for e.g. unused imports')
    print(" 6. Commit the changes on a new branch and create a typeshed PR")


if __name__ == "__main__":
    main()
