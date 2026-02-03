#!/usr/bin/env python3

"""Script to generate unannotated baseline stubs using stubgen.

Basic usage:
$ python3 scripts/create_baseline_stubs.py <project on PyPI>

Run with -h for more help.
"""

from __future__ import annotations

import argparse
import asyncio
import re
import subprocess
import sys
import urllib.parse
from http import HTTPStatus
from importlib.metadata import distribution
from pathlib import Path

import aiohttp
import termcolor

from ts_utils.paths import PYRIGHT_CONFIG, STDLIB_PATH, STUBS_PATH


def search_pip_freeze_output(project: str, output: str) -> tuple[str, str] | None:
    # Look for lines such as "typed-ast==1.4.2".  '-' matches '_' and
    # '_' matches '-' in project name, so that "typed_ast" matches
    # "typed-ast", and vice versa.
    regex = "^(" + re.sub(r"[-_]", "[-_]", project) + ")==(.*)"
    m = re.search(regex, output, flags=re.IGNORECASE | re.MULTILINE)
    if not m:
        return None
    return m.group(1), m.group(2)


def get_installed_package_info(project: str) -> tuple[str, str] | None:
    """Find package information from pip freeze output.

    Match project name somewhat fuzzily (case sensitive; '-' matches '_', and
    vice versa).

    Return (normalized project name, installed version) if successful.
    """
    # Not using "uv pip freeze" because if this is run from a global Python,
    # it'll mistakenly list the .venv's packages.
    r = subprocess.run(["pip", "freeze"], capture_output=True, text=True, check=True)
    return search_pip_freeze_output(project, r.stdout)


def run_stubgen(package: str, output: Path) -> None:
    print(f"Running stubgen: stubgen -o {output} -p {package}")
    subprocess.run(["stubgen", "-o", output, "-p", package, "--export-less"], check=True)


def run_stubdefaulter(stub_dir: Path) -> None:
    print(f"Running stubdefaulter: stubdefaulter --packages {stub_dir}")
    subprocess.run(["stubdefaulter", "--packages", stub_dir], check=False)


def run_black(stub_dir: Path) -> None:
    print(f"Running Black: black {stub_dir}")
    subprocess.run(["pre-commit", "run", "black", "--files", *stub_dir.rglob("*.pyi")], check=False)


def run_ruff(stub_dir: Path) -> None:
    print(f"Running Ruff: ruff check {stub_dir} --fix-only")
    subprocess.run([sys.executable, "-m", "ruff", "check", stub_dir, "--fix-only"], check=False)


async def get_project_urls_from_pypi(project: str, session: aiohttp.ClientSession) -> dict[str, str]:
    pypi_root = f"https://pypi.org/pypi/{urllib.parse.quote(project)}"
    async with session.get(f"{pypi_root}/json") as response:
        if response.status != HTTPStatus.OK:
            return {}
        j: dict[str, dict[str, dict[str, str]]]
        j = await response.json()
        return j["info"].get("project_urls") or {}


async def get_upstream_repo_url(project: str) -> str | None:
    # aiohttp is overkill here, but it would also just be silly
    # to have both requests and aiohttp in our requirements-tests.txt file.
    async with aiohttp.ClientSession() as session:
        project_urls = await get_project_urls_from_pypi(project, session)

        if not project_urls:
            return None

        # Order the project URLs so that we put the ones
        # that are most likely to point to the source code first
        urls_to_check: list[str] = []
        url_names_probably_pointing_to_source = ("Source", "Repository", "Homepage")
        for url_name in url_names_probably_pointing_to_source:
            if url := project_urls.get(url_name):
                urls_to_check.append(url)
        urls_to_check.extend(
            url for url_name, url in project_urls.items() if url_name not in url_names_probably_pointing_to_source
        )

        for url_to_check in urls_to_check:
            # Remove `www.`; replace `http://` with `https://`
            url = re.sub(r"^(https?://)?(www\.)?", "https://", url_to_check)
            netloc = urllib.parse.urlparse(url).netloc
            if netloc in {"gitlab.com", "github.com", "bitbucket.org", "foss.heptapod.net"}:
                # truncate to https://site.com/user/repo
                upstream_repo_url = "/".join(url.split("/")[:5])
                async with session.get(upstream_repo_url) as response:
                    if response.status == HTTPStatus.OK:
                        return upstream_repo_url
    return None


def create_metadata(project: str, stub_dir: Path, version: str) -> None:
    """Create a METADATA.toml file."""
    match = re.match(r"[0-9]+.[0-9]+", version)
    if match is None:
        sys.exit(f"Error: Cannot parse version number: {version}")
    filename = stub_dir / "METADATA.toml"
    version = match.group(0)
    if filename.exists():
        return
    metadata = f'version = "{version}.*"\n'
    upstream_repo_url = asyncio.run(get_upstream_repo_url(project))
    if upstream_repo_url is None:
        warning = (
            f"\nCould not find a URL pointing to the source code for {project!r}.\n"
            f"Please add it as `upstream_repository` to `stubs/{project}/METADATA.toml`, if possible!\n"
        )
        print(termcolor.colored(warning, "red"))
    else:
        metadata += f'upstream_repository = "{upstream_repo_url}"\n'
    print(f"Writing {filename}")
    filename.write_text(metadata, encoding="UTF-8")


def add_pyright_exclusion(stub_dir: Path) -> None:
    """Exclude stub_dir from strict pyright checks."""
    with PYRIGHT_CONFIG.open(encoding="UTF-8") as f:
        lines = f.readlines()
    i = 0
    while i < len(lines) and not lines[i].strip().startswith('"exclude": ['):
        i += 1
    assert i < len(lines), f"Error parsing {PYRIGHT_CONFIG}"
    while not lines[i].strip().startswith("]"):
        i += 1
    end = i

    # We assume that all third-party excludes must be at the end of the list.
    # This helps with skipping special entries, such as "stubs/**/@tests/test_cases".
    while lines[i - 1].strip().startswith('"stubs/'):
        i -= 1
    start = i

    before_third_party_excludes = lines[:start]
    third_party_excludes = lines[start:end]
    after_third_party_excludes = lines[end:]

    last_line = third_party_excludes[-1].rstrip()
    if not last_line.endswith(","):
        last_line += ","
        third_party_excludes[-1] = last_line + "\n"

    # Must use forward slash in the .json file
    line_to_add = f'        "{stub_dir.as_posix()}",\n'

    if line_to_add in third_party_excludes:
        print(f"{PYRIGHT_CONFIG} already up-to-date")
        return

    third_party_excludes.append(line_to_add)
    third_party_excludes.sort(key=str.lower)

    print(f"Updating {PYRIGHT_CONFIG}")
    with PYRIGHT_CONFIG.open("w", encoding="UTF-8") as f:
        f.writelines(before_third_party_excludes)
        f.writelines(third_party_excludes)
        f.writelines(after_third_party_excludes)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="""Generate baseline stubs automatically for an installed pip package
                       using stubgen. Also run Black and Ruff. If the name of
                       the project is different from the runtime Python package name, you may
                       need to use --package (example: --package yaml PyYAML)."""
    )
    parser.add_argument("project", help="name of PyPI project for which to generate stubs under stubs/")
    parser.add_argument("--package", help="generate stubs for this Python package (default is autodetected)")
    args = parser.parse_args()
    project = args.project
    package: str = args.package

    if not re.match(r"[a-zA-Z0-9-_.]+$", project):
        sys.exit(f"Invalid character in project name: {project!r}")

    if not package:
        package = project  # default
        # Try to find which packages are provided by the project
        # Use default if that fails or if several packages are found
        #
        # The importlib.metadata module is used for projects whose name is different
        # from the runtime Python package name (example: PyYAML/yaml)
        dist = distribution(project).read_text("top_level.txt")
        if dist is not None:
            packages = [name for name in dist.split() if not name.startswith("_")]
            if len(packages) == 1:
                package = packages[0]
        print(f'Using detected package "{package}" for project "{project}"', file=sys.stderr)
        print("Suggestion: Try again with --package argument if that's not what you wanted", file=sys.stderr)

    if not STUBS_PATH.is_dir() or not STDLIB_PATH.is_dir():
        sys.exit("Error: Current working directory must be the root of typeshed repository")

    # Get normalized project name and version of installed package.
    info = get_installed_package_info(project)
    if info is None:
        print(f'Error: "{project}" is not installed', file=sys.stderr)
        print(file=sys.stderr)
        print(f"Suggestion: Run `{sys.executable} -m pip install {project}` and try again", file=sys.stderr)
        sys.exit(1)
    project, version = info

    stub_dir = STUBS_PATH / project
    package_dir = stub_dir / package
    if package_dir.exists():
        sys.exit(f"Error: {package_dir} already exists (delete it first)")

    run_stubgen(package, stub_dir)
    run_stubdefaulter(stub_dir)

    run_ruff(stub_dir)
    run_black(stub_dir)

    create_metadata(project, stub_dir, version)

    # Since the generated stubs won't have many type annotations, we
    # have to exclude them from strict pyright checks.
    add_pyright_exclusion(stub_dir)

    print("\nDone!\n\nSuggested next steps:")
    print(f" 1. Manually review the generated stubs in {stub_dir}")
    print(" 2. Optionally run tests and autofixes (see tests/README.md for details)")
    print(" 3. Commit the changes on a new branch and create a typeshed PR (don't force-push!)")


if __name__ == "__main__":
    main()
