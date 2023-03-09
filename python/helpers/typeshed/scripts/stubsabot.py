#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
import datetime
import enum
import functools
import io
import os
import re
import subprocess
import sys
import tarfile
import urllib.parse
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING, Any, TypeVar

import aiohttp
import packaging.specifiers
import packaging.version
import tomli
import tomlkit

if TYPE_CHECKING:

    def colored(__str: str, __style: str) -> str:
        ...

else:
    from termcolor import colored


ActionLevelSelf = TypeVar("ActionLevelSelf", bound="ActionLevel")


class ActionLevel(enum.IntEnum):
    def __new__(cls: type[ActionLevelSelf], value: int, doc: str) -> ActionLevelSelf:
        member = int.__new__(cls, value)
        member._value_ = value
        member.__doc__ = doc
        return member

    nothing = 0, "make no changes"
    local = 1, "make changes that affect local repo"
    fork = 2, "make changes that affect remote repo, but won't open PRs against upstream"
    everything = 3, "do everything, e.g. open PRs"


@dataclass
class StubInfo:
    distribution: str
    version_spec: str
    obsolete: bool
    no_longer_updated: bool


def read_typeshed_stub_metadata(stub_path: Path) -> StubInfo:
    with (stub_path / "METADATA.toml").open("rb") as f:
        meta = tomli.load(f)
    return StubInfo(
        distribution=stub_path.name,
        version_spec=meta["version"],
        obsolete="obsolete_since" in meta,
        no_longer_updated=meta.get("no_longer_updated", False),
    )


@dataclass
class PypiInfo:
    distribution: str
    version: packaging.version.Version
    upload_date: datetime.datetime
    # https://warehouse.pypa.io/api-reference/json.html#get--pypi--project_name--json
    # Corresponds to a single entry from `releases` for the given version
    release_to_download: dict[str, Any]
    info: dict[str, Any]


async def fetch_pypi_info(distribution: str, session: aiohttp.ClientSession) -> PypiInfo:
    url = f"https://pypi.org/pypi/{urllib.parse.quote(distribution)}/json"
    async with session.get(url) as response:
        response.raise_for_status()
        j = await response.json()
        version = j["info"]["version"]
        # prefer wheels, since it's what most users will get / it's pretty easy to mess up MANIFEST
        release_to_download = sorted(j["releases"][version], key=lambda x: bool(x["packagetype"] == "bdist_wheel"))[-1]
        date = datetime.datetime.fromisoformat(release_to_download["upload_time"])
        return PypiInfo(
            distribution=distribution,
            version=packaging.version.Version(version),
            upload_date=date,
            release_to_download=release_to_download,
            info=j["info"],
        )


@dataclass
class Update:
    distribution: str
    stub_path: Path
    old_version_spec: str
    new_version_spec: str
    links: dict[str, str]

    def __str__(self) -> str:
        return f"Updating {self.distribution} from {self.old_version_spec!r} to {self.new_version_spec!r}"


@dataclass
class Obsolete:
    distribution: str
    stub_path: Path
    obsolete_since_version: str
    obsolete_since_date: datetime.datetime
    links: dict[str, str]

    def __str__(self) -> str:
        return f"Marking {self.distribution} as obsolete since {self.obsolete_since_version!r}"


@dataclass
class NoUpdate:
    distribution: str
    reason: str

    def __str__(self) -> str:
        return f"Skipping {self.distribution}: {self.reason}"


async def package_contains_py_typed(release_to_download: dict[str, Any], session: aiohttp.ClientSession) -> bool:
    async with session.get(release_to_download["url"]) as response:
        body = io.BytesIO(await response.read())

    packagetype = release_to_download["packagetype"]
    if packagetype == "bdist_wheel":
        assert release_to_download["filename"].endswith(".whl")
        with zipfile.ZipFile(body) as zf:
            return any(Path(f).name == "py.typed" for f in zf.namelist())
    elif packagetype == "sdist":
        assert release_to_download["filename"].endswith(".tar.gz")
        with tarfile.open(fileobj=body, mode="r:gz") as zf:
            return any(Path(f).name == "py.typed" for f in zf.getnames())
    else:
        raise AssertionError(f"Unknown package type: {packagetype}")


def _check_spec(updated_spec: str, version: packaging.version.Version) -> str:
    assert version in packaging.specifiers.SpecifierSet("==" + updated_spec), f"{version} not in {updated_spec}"
    return updated_spec


def get_updated_version_spec(spec: str, version: packaging.version.Version) -> str:
    """
    Given the old specifier and an updated version, returns an updated specifier that has the
    specificity of the old specifier, but matches the updated version.

    For example:
    spec="1", version="1.2.3" -> "1.2.3"
    spec="1.0.1", version="1.2.3" -> "1.2.3"
    spec="1.*", version="1.2.3" -> "1.*"
    spec="1.*", version="2.3.4" -> "2.*"
    spec="1.1.*", version="1.2.3" -> "1.2.*"
    spec="1.1.1.*", version="1.2.3" -> "1.2.3.*"
    """
    if not spec.endswith(".*"):
        return _check_spec(version.base_version, version)

    specificity = spec.count(".") if spec.removesuffix(".*") else 0
    rounded_version = version.base_version.split(".")[:specificity]
    rounded_version.extend(["0"] * (specificity - len(rounded_version)))

    return _check_spec(".".join(rounded_version) + ".*", version)


async def determine_action(stub_path: Path, session: aiohttp.ClientSession) -> Update | NoUpdate | Obsolete:
    stub_info = read_typeshed_stub_metadata(stub_path)
    if stub_info.obsolete:
        return NoUpdate(stub_info.distribution, "obsolete")
    if stub_info.no_longer_updated:
        return NoUpdate(stub_info.distribution, "no longer updated")

    pypi_info = await fetch_pypi_info(stub_info.distribution, session)
    spec = packaging.specifiers.SpecifierSet("==" + stub_info.version_spec)
    if pypi_info.version in spec:
        return NoUpdate(stub_info.distribution, "up to date")

    project_urls = pypi_info.info["project_urls"] or {}
    maybe_links: dict[str, str | None] = {
        "Release": pypi_info.info["release_url"],
        "Homepage": project_urls.get("Homepage"),
        "Changelog": project_urls.get("Changelog") or project_urls.get("Changes") or project_urls.get("Change Log"),
    }
    links = {k: v for k, v in maybe_links.items() if v is not None}

    if await package_contains_py_typed(pypi_info.release_to_download, session):
        return Obsolete(
            stub_info.distribution,
            stub_path,
            obsolete_since_version=str(pypi_info.version),
            obsolete_since_date=pypi_info.upload_date,
            links=links,
        )

    return Update(
        distribution=stub_info.distribution,
        stub_path=stub_path,
        old_version_spec=stub_info.version_spec,
        new_version_spec=get_updated_version_spec(stub_info.version_spec, pypi_info.version),
        links=links,
    )


TYPESHED_OWNER = "python"


@functools.lru_cache()
def get_origin_owner() -> str:
    output = subprocess.check_output(["git", "remote", "get-url", "origin"], text=True).strip()
    match = re.match(r"(git@github.com:|https://github.com/)(?P<owner>[^/]+)/(?P<repo>[^/\s]+)", output)
    assert match is not None, f"Couldn't identify origin's owner: {output!r}"
    assert match.group("repo").removesuffix(".git") == "typeshed", f'Unexpected repo: {match.group("repo")!r}'
    return match.group("owner")


async def create_or_update_pull_request(*, title: str, body: str, branch_name: str, session: aiohttp.ClientSession) -> None:
    secret = os.environ["GITHUB_TOKEN"]
    if secret.startswith("ghp"):
        auth = f"token {secret}"
    else:
        auth = f"Bearer {secret}"

    fork_owner = get_origin_owner()

    async with session.post(
        f"https://api.github.com/repos/{TYPESHED_OWNER}/typeshed/pulls",
        json={"title": title, "body": body, "head": f"{fork_owner}:{branch_name}", "base": "master"},
        headers={"Accept": "application/vnd.github.v3+json", "Authorization": auth},
    ) as response:
        resp_json = await response.json()
        if response.status == 422 and any(
            "A pull request already exists" in e.get("message", "") for e in resp_json.get("errors", [])
        ):
            # Find the existing PR
            async with session.get(
                f"https://api.github.com/repos/{TYPESHED_OWNER}/typeshed/pulls",
                params={"state": "open", "head": f"{fork_owner}:{branch_name}", "base": "master"},
                headers={"Accept": "application/vnd.github.v3+json", "Authorization": auth},
            ) as response:
                response.raise_for_status()
                resp_json = await response.json()
                assert len(resp_json) >= 1
                pr_number = resp_json[0]["number"]
            # Update the PR's title and body
            async with session.patch(
                f"https://api.github.com/repos/{TYPESHED_OWNER}/typeshed/pulls/{pr_number}",
                json={"title": title, "body": body},
                headers={"Accept": "application/vnd.github.v3+json", "Authorization": auth},
            ) as response:
                response.raise_for_status()
            return
        response.raise_for_status()


def origin_branch_has_changes(branch: str) -> bool:
    assert not branch.startswith("origin/")
    try:
        # number of commits on origin/branch that are not on branch or are
        # patch equivalent to a commit on branch
        output = subprocess.check_output(
            ["git", "rev-list", "--right-only", "--cherry-pick", "--count", f"{branch}...origin/{branch}"],
            stderr=subprocess.DEVNULL,
        )
    except subprocess.CalledProcessError:
        # origin/branch does not exist
        return False
    return int(output) > 0


class RemoteConflict(Exception):
    pass


def somewhat_safe_force_push(branch: str) -> None:
    if origin_branch_has_changes(branch):
        raise RemoteConflict(f"origin/{branch} has changes not on {branch}!")
    subprocess.check_call(["git", "push", "origin", branch, "--force"])


def normalize(name: str) -> str:
    # PEP 503 normalization
    return re.sub(r"[-_.]+", "-", name).lower()


# lock should be unnecessary, but can't hurt to enforce mutual exclusion
_repo_lock = asyncio.Lock()

BRANCH_PREFIX = "stubsabot"


async def suggest_typeshed_update(update: Update, session: aiohttp.ClientSession, action_level: ActionLevel) -> None:
    if action_level <= ActionLevel.nothing:
        return
    title = f"[stubsabot] Bump {update.distribution} to {update.new_version_spec}"
    async with _repo_lock:
        branch_name = f"{BRANCH_PREFIX}/{normalize(update.distribution)}"
        subprocess.check_call(["git", "checkout", "-B", branch_name, "origin/master"])
        with open(update.stub_path / "METADATA.toml", "rb") as f:
            meta = tomlkit.load(f)
        meta["version"] = update.new_version_spec
        with open(update.stub_path / "METADATA.toml", "w") as f:
            tomlkit.dump(meta, f)
        subprocess.check_call(["git", "commit", "--all", "-m", title])
        if action_level <= ActionLevel.local:
            return
        somewhat_safe_force_push(branch_name)
        if action_level <= ActionLevel.fork:
            return

    body = "\n".join(f"{k}: {v}" for k, v in update.links.items())
    body += """

If stubtest fails for this PR:
- Leave this PR open (as a reminder, and to prevent stubsabot from opening another PR)
- Fix stubtest failures in another PR, then close this PR
"""
    await create_or_update_pull_request(title=title, body=body, branch_name=branch_name, session=session)


async def suggest_typeshed_obsolete(obsolete: Obsolete, session: aiohttp.ClientSession, action_level: ActionLevel) -> None:
    if action_level <= ActionLevel.nothing:
        return
    title = f"[stubsabot] Mark {obsolete.distribution} as obsolete since {obsolete.obsolete_since_version}"
    async with _repo_lock:
        branch_name = f"{BRANCH_PREFIX}/{normalize(obsolete.distribution)}"
        subprocess.check_call(["git", "checkout", "-B", branch_name, "origin/master"])
        with open(obsolete.stub_path / "METADATA.toml", "rb") as f:
            meta = tomlkit.load(f)
        obs_string = tomlkit.string(obsolete.obsolete_since_version)
        obs_string.comment(f"Released on {obsolete.obsolete_since_date.date().isoformat()}")
        meta["obsolete_since"] = obs_string
        with open(obsolete.stub_path / "METADATA.toml", "w") as f:
            tomlkit.dump(meta, f)
        subprocess.check_call(["git", "commit", "--all", "-m", title])
        if action_level <= ActionLevel.local:
            return
        somewhat_safe_force_push(branch_name)
        if action_level <= ActionLevel.fork:
            return

    body = "\n".join(f"{k}: {v}" for k, v in obsolete.links.items())
    await create_or_update_pull_request(title=title, body=body, branch_name=branch_name, session=session)


async def main() -> None:
    assert sys.version_info >= (3, 9)

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--action-level",
        type=lambda x: getattr(ActionLevel, x),  # type: ignore[no-any-return]
        default=ActionLevel.everything,
        help="Limit actions performed to achieve dry runs for different levels of dryness",
    )
    parser.add_argument(
        "--action-count-limit",
        type=int,
        default=None,
        help="Limit number of actions performed and the remainder are logged. Useful for testing",
    )
    args = parser.parse_args()

    if args.action_level > ActionLevel.fork:
        if os.environ.get("GITHUB_TOKEN") is None:
            raise ValueError("GITHUB_TOKEN environment variable must be set")

    denylist = {"gdb"}  # gdb is not a pypi distribution

    if args.action_level >= ActionLevel.fork:
        subprocess.check_call(["git", "fetch", "--prune", "--all"])

    try:
        conn = aiohttp.TCPConnector(limit_per_host=10)
        async with aiohttp.ClientSession(connector=conn) as session:
            tasks = [
                asyncio.create_task(determine_action(stubs_path, session))
                for stubs_path in Path("stubs").iterdir()
                if stubs_path.name not in denylist
            ]

            action_count = 0
            for task in asyncio.as_completed(tasks):
                update = await task
                print(update)

                if isinstance(update, NoUpdate):
                    continue

                if args.action_count_limit is not None and action_count >= args.action_count_limit:
                    print(colored("... but we've reached action count limit", "red"))
                    continue
                action_count += 1

                try:
                    if isinstance(update, Update):
                        await suggest_typeshed_update(update, session, action_level=args.action_level)
                        continue
                    if isinstance(update, Obsolete):
                        await suggest_typeshed_obsolete(update, session, action_level=args.action_level)
                        continue
                except RemoteConflict as e:
                    print(colored(f"... but ran into {type(e).__qualname__}: {e}", "red"))
                    continue
                raise AssertionError
    finally:
        # if you need to cleanup, try:
        # git branch -D $(git branch --list 'stubsabot/*')
        if args.action_level >= ActionLevel.local:
            subprocess.check_call(["git", "checkout", "master"])


if __name__ == "__main__":
    asyncio.run(main())
