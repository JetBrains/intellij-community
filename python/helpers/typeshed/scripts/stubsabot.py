#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
import contextlib
import datetime
import enum
import functools
import io
import os
import re
import subprocess
import sys
import tarfile
import textwrap
import urllib.parse
import zipfile
from collections.abc import Iterator, Mapping, Sequence
from dataclasses import dataclass
from http import HTTPStatus
from pathlib import Path
from typing import Annotated, Any, ClassVar, NamedTuple
from typing_extensions import Self, TypeAlias

import aiohttp
import packaging.specifiers
import packaging.version
import tomli
import tomlkit
from termcolor import colored

TYPESHED_OWNER = "python"
TYPESHED_API_URL = f"https://api.github.com/repos/{TYPESHED_OWNER}/typeshed"

STUBSABOT_LABEL = "stubsabot"


class ActionLevel(enum.IntEnum):
    def __new__(cls, value: int, doc: str) -> Self:
        member = int.__new__(cls, value)
        member._value_ = value
        member.__doc__ = doc
        return member

    @classmethod
    def from_cmd_arg(cls, cmd_arg: str) -> ActionLevel:
        try:
            return cls[cmd_arg]
        except KeyError:
            raise argparse.ArgumentTypeError(f'Argument must be one of "{list(cls.__members__)}"')

    nothing = 0, "make no changes"
    local = 1, "make changes that affect local repo"
    fork = 2, "make changes that affect remote repo, but won't open PRs against upstream"
    everything = 3, "do everything, e.g. open PRs"


@dataclass
class StubInfo:
    distribution: str
    version_spec: str
    upstream_repository: str | None
    obsolete: bool
    no_longer_updated: bool


def read_typeshed_stub_metadata(stub_path: Path) -> StubInfo:
    with (stub_path / "METADATA.toml").open("rb") as f:
        meta = tomli.load(f)
    return StubInfo(
        distribution=stub_path.name,
        version_spec=meta["version"],
        upstream_repository=meta.get("upstream_repository"),
        obsolete="obsolete_since" in meta,
        no_longer_updated=meta.get("no_longer_updated", False),
    )


@dataclass
class PypiReleaseDownload:
    url: str
    packagetype: Annotated[str, "Should hopefully be either 'bdist_wheel' or 'sdist'"]
    filename: str
    version: packaging.version.Version
    upload_date: datetime.datetime


VersionString: TypeAlias = str
ReleaseDownload: TypeAlias = dict[str, Any]


def _best_effort_version(version: VersionString) -> packaging.version.Version:
    try:
        return packaging.version.Version(version)
    except packaging.version.InvalidVersion:
        # packaging.version.Version no longer parses legacy versions
        try:
            return packaging.version.Version(version.replace("-", "+"))
        except packaging.version.InvalidVersion:
            return packaging.version.Version("0")


@dataclass
class PypiInfo:
    distribution: str
    pypi_root: str
    releases: dict[VersionString, list[ReleaseDownload]]
    info: dict[str, Any]

    def get_release(self, *, version: VersionString) -> PypiReleaseDownload:
        # prefer wheels, since it's what most users will get / it's pretty easy to mess up MANIFEST
        release_info = sorted(self.releases[version], key=lambda x: bool(x["packagetype"] == "bdist_wheel"))[-1]
        return PypiReleaseDownload(
            url=release_info["url"],
            packagetype=release_info["packagetype"],
            filename=release_info["filename"],
            version=packaging.version.Version(version),
            upload_date=datetime.datetime.fromisoformat(release_info["upload_time"]),
        )

    def get_latest_release(self) -> PypiReleaseDownload:
        return self.get_release(version=self.info["version"])

    def releases_in_descending_order(self) -> Iterator[PypiReleaseDownload]:
        for version in sorted(self.releases, key=_best_effort_version, reverse=True):
            yield self.get_release(version=version)


async def fetch_pypi_info(distribution: str, session: aiohttp.ClientSession) -> PypiInfo:
    # Cf. # https://warehouse.pypa.io/api-reference/json.html#get--pypi--project_name--json
    pypi_root = f"https://pypi.org/pypi/{urllib.parse.quote(distribution)}"
    async with session.get(f"{pypi_root}/json") as response:
        response.raise_for_status()
        j = await response.json()
        return PypiInfo(distribution=distribution, pypi_root=pypi_root, releases=j["releases"], info=j["info"])


@dataclass
class Update:
    distribution: str
    stub_path: Path
    old_version_spec: str
    new_version_spec: str
    links: dict[str, str]
    diff_analysis: DiffAnalysis | None

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


async def release_contains_py_typed(release_to_download: PypiReleaseDownload, *, session: aiohttp.ClientSession) -> bool:
    async with session.get(release_to_download.url) as response:
        body = io.BytesIO(await response.read())

    packagetype = release_to_download.packagetype
    if packagetype == "bdist_wheel":
        assert release_to_download.filename.endswith(".whl")
        with zipfile.ZipFile(body) as zf:
            return any(Path(f).name == "py.typed" for f in zf.namelist())
    elif packagetype == "sdist":
        assert release_to_download.filename.endswith(".tar.gz")
        with tarfile.open(fileobj=body, mode="r:gz") as zf:
            return any(Path(f).name == "py.typed" for f in zf.getnames())
    else:
        raise AssertionError(f"Unknown package type: {packagetype!r}")


async def find_first_release_with_py_typed(pypi_info: PypiInfo, *, session: aiohttp.ClientSession) -> PypiReleaseDownload | None:
    """If the latest release is py.typed, return the first release that included a py.typed file.

    If the latest release is not py.typed, return None.
    """
    release_iter = (release for release in pypi_info.releases_in_descending_order() if not release.version.is_prerelease)
    latest_release = next(release_iter)
    # If the latest release is not py.typed, assume none are.
    if not (await release_contains_py_typed(latest_release, session=session)):
        return None

    first_release_with_py_typed = latest_release
    while await release_contains_py_typed(release := next(release_iter), session=session):
        first_release_with_py_typed = release
    return first_release_with_py_typed


def _check_spec(updated_spec: str, version: packaging.version.Version) -> str:
    assert version in packaging.specifiers.SpecifierSet(f"=={updated_spec}"), f"{version} not in {updated_spec}"
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
        return _check_spec(str(version), version)

    specificity = spec.count(".") if spec.removesuffix(".*") else 0
    rounded_version = version.base_version.split(".")[:specificity]
    rounded_version.extend(["0"] * (specificity - len(rounded_version)))

    return _check_spec(".".join(rounded_version) + ".*", version)


@functools.cache
def get_github_api_headers() -> Mapping[str, str]:
    headers = {"Accept": "application/vnd.github.v3+json"}
    secret = os.environ.get("GITHUB_TOKEN")
    if secret is not None:
        headers["Authorization"] = f"token {secret}" if secret.startswith("ghp") else f"Bearer {secret}"
    return headers


@dataclass
class GithubInfo:
    repo_path: str
    tags: list[dict[str, Any]]


async def get_github_repo_info(session: aiohttp.ClientSession, stub_info: StubInfo) -> GithubInfo | None:
    """
    If the project represented by `stub_info` is hosted on GitHub,
    return information regarding the project as it exists on GitHub.

    Else, return None.
    """
    if stub_info.upstream_repository:
        # We have various sanity checks for the upstream_repository field in tests/parse_metadata.py,
        # so no need to repeat all of them here
        split_url = urllib.parse.urlsplit(stub_info.upstream_repository)
        if split_url.netloc == "github.com":
            url_path = split_url.path.strip("/")
            assert len(Path(url_path).parts) == 2
            github_tags_info_url = f"https://api.github.com/repos/{url_path}/tags"
            async with session.get(github_tags_info_url, headers=get_github_api_headers()) as response:
                if response.status == 200:
                    tags: list[dict[str, Any]] = await response.json()
                    assert isinstance(tags, list)
                    return GithubInfo(repo_path=url_path, tags=tags)
    return None


class GithubDiffInfo(NamedTuple):
    repo_path: str
    old_tag: str
    new_tag: str
    diff_url: str


async def get_diff_info(
    session: aiohttp.ClientSession, stub_info: StubInfo, pypi_version: packaging.version.Version
) -> GithubDiffInfo | None:
    """Return a tuple giving info about the diff between two releases, if possible.

    Return `None` if the project isn't hosted on GitHub,
    or if a link pointing to the diff couldn't be found for any other reason.
    """
    github_info = await get_github_repo_info(session, stub_info)
    if github_info is None:
        return None

    versions_to_tags: dict[packaging.version.Version, str] = {}
    for tag in github_info.tags:
        tag_name = tag["name"]
        # Some packages in typeshed have tag names
        # that are invalid to be passed to the Version() constructor,
        # e.g. v.1.4.2
        with contextlib.suppress(packaging.version.InvalidVersion):
            versions_to_tags[packaging.version.Version(tag_name)] = tag_name

    curr_specifier = packaging.specifiers.SpecifierSet(f"=={stub_info.version_spec}")

    try:
        new_tag = versions_to_tags[pypi_version]
    except KeyError:
        return None

    try:
        old_version = max(version for version in versions_to_tags if version in curr_specifier and version < pypi_version)
    except ValueError:
        return None
    else:
        old_tag = versions_to_tags[old_version]

    diff_url = f"https://github.com/{github_info.repo_path}/compare/{old_tag}...{new_tag}"
    return GithubDiffInfo(repo_path=github_info.repo_path, old_tag=old_tag, new_tag=new_tag, diff_url=diff_url)


FileInfo: TypeAlias = dict[str, Any]


def _plural_s(num: int, /) -> str:
    return "s" if num != 1 else ""


@dataclass
class DiffAnalysis:
    MAXIMUM_NUMBER_OF_FILES_TO_LIST: ClassVar[int] = 7
    py_files: list[FileInfo]
    py_files_stubbed_in_typeshed: list[FileInfo]

    @property
    def runtime_definitely_has_consistent_directory_structure_with_typeshed(self) -> bool:
        """
        If 0 .py files in the GitHub diff exist in typeshed's stubs,
        there's a possibility that the .py files might be found
        in a different directory at runtime.

        For example: pyopenssl has its .py files in the `src/OpenSSL/` directory at runtime,
        but in typeshed the stubs are in the `OpenSSL/` directory.
        """
        return bool(self.py_files_stubbed_in_typeshed)

    @functools.cached_property
    def public_files_added(self) -> Sequence[str]:
        return [
            file["filename"]
            for file in self.py_files
            if not re.match("_[^_]", Path(file["filename"]).name) and file["status"] == "added"
        ]

    @functools.cached_property
    def typeshed_files_deleted(self) -> Sequence[str]:
        return [file["filename"] for file in self.py_files_stubbed_in_typeshed if file["status"] == "removed"]

    @functools.cached_property
    def typeshed_files_modified(self) -> Sequence[str]:
        return [file["filename"] for file in self.py_files_stubbed_in_typeshed if file["status"] in {"modified", "renamed"}]

    @property
    def total_lines_added(self) -> int:
        return sum(file["additions"] for file in self.py_files)

    @property
    def total_lines_deleted(self) -> int:
        return sum(file["deletions"] for file in self.py_files)

    def _describe_files(self, *, verb: str, filenames: Sequence[str]) -> str:
        num_files = len(filenames)
        if num_files > 1:
            description = f"have been {verb}"
            # Don't list the filenames if there are *loads* of files
            if num_files <= self.MAXIMUM_NUMBER_OF_FILES_TO_LIST:
                description += ": "
                description += ", ".join(f"`{filename}`" for filename in filenames)
            description += "."
            return description
        if num_files == 1:
            return f"has been {verb}: `{filenames[0]}`."
        return f"have been {verb}."

    def describe_public_files_added(self) -> str:
        num_files_added = len(self.public_files_added)
        analysis = f"{num_files_added} public Python file{_plural_s(num_files_added)} "
        analysis += self._describe_files(verb="added", filenames=self.public_files_added)
        return analysis

    def describe_typeshed_files_deleted(self) -> str:
        num_files_deleted = len(self.typeshed_files_deleted)
        analysis = f"{num_files_deleted} file{_plural_s(num_files_deleted)} included in typeshed's stubs "
        analysis += self._describe_files(verb="deleted", filenames=self.typeshed_files_deleted)
        return analysis

    def describe_typeshed_files_modified(self) -> str:
        num_files_modified = len(self.typeshed_files_modified)
        analysis = f"{num_files_modified} file{_plural_s(num_files_modified)} included in typeshed's stubs "
        analysis += self._describe_files(verb="modified or renamed", filenames=self.typeshed_files_modified)
        return analysis

    def __str__(self) -> str:
        data_points: list[str] = []
        if self.runtime_definitely_has_consistent_directory_structure_with_typeshed:
            data_points += [
                self.describe_public_files_added(),
                self.describe_typeshed_files_deleted(),
                self.describe_typeshed_files_modified(),
            ]
        data_points += [
            f"Total lines of Python code added: {self.total_lines_added}.",
            f"Total lines of Python code deleted: {self.total_lines_deleted}.",
        ]
        return "Stubsabot analysis of the diff between the two releases:\n - " + "\n - ".join(data_points)


async def analyze_diff(
    github_repo_path: str, stub_path: Path, old_tag: str, new_tag: str, *, session: aiohttp.ClientSession
) -> DiffAnalysis | None:
    url = f"https://api.github.com/repos/{github_repo_path}/compare/{old_tag}...{new_tag}"
    async with session.get(url, headers=get_github_api_headers()) as response:
        response.raise_for_status()
        json_resp: dict[str, list[FileInfo]] = await response.json()
        assert isinstance(json_resp, dict)
    # https://docs.github.com/en/rest/commits/commits#compare-two-commits
    py_files: list[FileInfo] = [file for file in json_resp["files"] if Path(file["filename"]).suffix == ".py"]
    files_in_typeshed = set(stub_path.rglob("*.pyi"))
    py_files_stubbed_in_typeshed = [file for file in py_files if (stub_path / f"{file['filename']}i") in files_in_typeshed]
    return DiffAnalysis(py_files=py_files, py_files_stubbed_in_typeshed=py_files_stubbed_in_typeshed)


async def determine_action(stub_path: Path, session: aiohttp.ClientSession) -> Update | NoUpdate | Obsolete:
    stub_info = read_typeshed_stub_metadata(stub_path)
    if stub_info.obsolete:
        return NoUpdate(stub_info.distribution, "obsolete")
    if stub_info.no_longer_updated:
        return NoUpdate(stub_info.distribution, "no longer updated")

    pypi_info = await fetch_pypi_info(stub_info.distribution, session)
    latest_release = pypi_info.get_latest_release()
    latest_version = latest_release.version
    spec = packaging.specifiers.SpecifierSet(f"=={stub_info.version_spec}")
    obsolete_since = await find_first_release_with_py_typed(pypi_info, session=session)
    if obsolete_since is None and latest_version in spec:
        return NoUpdate(stub_info.distribution, "up to date")

    relevant_version = obsolete_since.version if obsolete_since else latest_version

    project_urls = pypi_info.info["project_urls"] or {}
    maybe_links: dict[str, str | None] = {
        "Release": f"{pypi_info.pypi_root}/{relevant_version}",
        "Homepage": project_urls.get("Homepage"),
        "Repository": stub_info.upstream_repository,
        "Changelog": project_urls.get("Changelog") or project_urls.get("Changes") or project_urls.get("Change Log"),
    }
    links = {k: v for k, v in maybe_links.items() if v is not None}

    diff_info = await get_diff_info(session, stub_info, relevant_version)
    if diff_info is not None:
        links["Diff"] = diff_info.diff_url

    if obsolete_since:
        return Obsolete(
            stub_info.distribution,
            stub_path,
            obsolete_since_version=str(obsolete_since.version),
            obsolete_since_date=obsolete_since.upload_date,
            links=links,
        )

    if diff_info is None:
        diff_analysis: DiffAnalysis | None = None
    else:
        diff_analysis = await analyze_diff(
            github_repo_path=diff_info.repo_path,
            stub_path=stub_path,
            old_tag=diff_info.old_tag,
            new_tag=diff_info.new_tag,
            session=session,
        )

    return Update(
        distribution=stub_info.distribution,
        stub_path=stub_path,
        old_version_spec=stub_info.version_spec,
        new_version_spec=get_updated_version_spec(stub_info.version_spec, latest_version),
        links=links,
        diff_analysis=diff_analysis,
    )


@functools.lru_cache()
def get_origin_owner() -> str:
    output = subprocess.check_output(["git", "remote", "get-url", "origin"], text=True).strip()
    match = re.match(r"(git@github.com:|https://github.com/)(?P<owner>[^/]+)/(?P<repo>[^/\s]+)", output)
    assert match is not None, f"Couldn't identify origin's owner: {output!r}"
    assert match.group("repo").removesuffix(".git") == "typeshed", f'Unexpected repo: {match.group("repo")!r}'
    return match.group("owner")


async def create_or_update_pull_request(*, title: str, body: str, branch_name: str, session: aiohttp.ClientSession) -> None:
    fork_owner = get_origin_owner()

    async with session.post(
        f"{TYPESHED_API_URL}/pulls",
        json={"title": title, "body": body, "head": f"{fork_owner}:{branch_name}", "base": "main"},
        headers=get_github_api_headers(),
    ) as response:
        resp_json = await response.json()
        if response.status == HTTPStatus.CREATED:
            pr_number = resp_json["number"]
            assert isinstance(pr_number, int)
        elif response.status == HTTPStatus.UNPROCESSABLE_ENTITY and any(
            "A pull request already exists" in e.get("message", "") for e in resp_json.get("errors", [])
        ):
            pr_number = await update_existing_pull_request(title=title, body=body, branch_name=branch_name, session=session)
        else:
            response.raise_for_status()
            raise AssertionError(f"Unexpected response: {response.status}")
    await update_pull_request_label(pr_number=pr_number, session=session)


async def update_existing_pull_request(*, title: str, body: str, branch_name: str, session: aiohttp.ClientSession) -> int:
    fork_owner = get_origin_owner()

    # Find the existing PR
    async with session.get(
        f"{TYPESHED_API_URL}/pulls",
        params={"state": "open", "head": f"{fork_owner}:{branch_name}", "base": "main"},
        headers=get_github_api_headers(),
    ) as response:
        response.raise_for_status()
        resp_json = await response.json()
        assert len(resp_json) >= 1
        pr_number = resp_json[0]["number"]
        assert isinstance(pr_number, int)
    # Update the PR's title and body
    async with session.patch(
        f"{TYPESHED_API_URL}/pulls/{pr_number}", json={"title": title, "body": body}, headers=get_github_api_headers()
    ) as response:
        response.raise_for_status()
    return pr_number


async def update_pull_request_label(*, pr_number: int, session: aiohttp.ClientSession) -> None:
    # There is no pulls/.../labels endpoint, which is why we need to use the issues endpoint.
    async with session.post(
        f"{TYPESHED_API_URL}/issues/{pr_number}/labels", json={"labels": [STUBSABOT_LABEL]}, headers=get_github_api_headers()
    ) as response:
        response.raise_for_status()


def has_non_stubsabot_commits(branch: str) -> bool:
    assert not branch.startswith("origin/")
    try:
        # commits on origin/branch that are not on branch or are
        # patch equivalent to a commit on branch
        print(
            "[debugprint]",
            subprocess.check_output(
                ["git", "log", "--right-only", "--pretty=%an %s", "--cherry-pick", f"{branch}...origin/{branch}"]
            ),
        )
        print(
            "[debugprint]",
            subprocess.check_output(
                ["git", "log", "--right-only", "--pretty=%an", "--cherry-pick", f"{branch}...origin/{branch}"]
            ),
        )
        output = subprocess.check_output(
            ["git", "log", "--right-only", "--pretty=%an", "--cherry-pick", f"{branch}...origin/{branch}"],
            stderr=subprocess.DEVNULL,
        )
        return bool(set(output.splitlines()) - {b"stubsabot"})
    except subprocess.CalledProcessError:
        # origin/branch does not exist
        return False


def latest_commit_is_different_to_last_commit_on_origin(branch: str) -> bool:
    assert not branch.startswith("origin/")
    try:
        # https://www.git-scm.com/docs/git-range-diff
        # If the number of lines is >1,
        # it indicates that something about our commit is different to the last commit
        # (Could be the commit "content", or the commit message).
        commit_comparison = subprocess.run(
            ["git", "range-diff", f"origin/{branch}~1..origin/{branch}", "HEAD~1..HEAD"], check=True, capture_output=True
        )
        return len(commit_comparison.stdout.splitlines()) > 1
    except subprocess.CalledProcessError:
        # origin/branch does not exist
        return True


class RemoteConflict(Exception):
    pass


def somewhat_safe_force_push(branch: str) -> None:
    if has_non_stubsabot_commits(branch):
        raise RemoteConflict(f"origin/{branch} has non-stubsabot changes that are not on {branch}!")
    subprocess.check_call(["git", "push", "origin", branch, "--force"])


def normalize(name: str) -> str:
    # PEP 503 normalization
    return re.sub(r"[-_.]+", "-", name).lower()


# lock should be unnecessary, but can't hurt to enforce mutual exclusion
_repo_lock = asyncio.Lock()

BRANCH_PREFIX = "stubsabot"


def get_update_pr_body(update: Update, metadata: dict[str, Any]) -> str:
    body = "\n".join(f"{k}: {v}" for k, v in update.links.items())

    if update.diff_analysis is not None:
        body += f"\n\n{update.diff_analysis}"

    stubtest_settings: dict[str, Any] = metadata.get("tool", {}).get("stubtest", {})
    stubtest_will_run = not stubtest_settings.get("skip", False)
    if stubtest_will_run:
        body += textwrap.dedent(
            """

            If stubtest fails for this PR:
            - Leave this PR open (as a reminder, and to prevent stubsabot from opening another PR)
            - Fix stubtest failures in another PR, then close this PR

            Note that you will need to close and re-open the PR in order to trigger CI
            """
        )
    else:
        body += textwrap.dedent(
            f"""

            :warning: Review this PR manually, as stubtest is skipped in CI for {update.distribution}! :warning:
            """
        )
    return body


async def suggest_typeshed_update(update: Update, session: aiohttp.ClientSession, action_level: ActionLevel) -> None:
    if action_level <= ActionLevel.nothing:
        return
    title = f"[stubsabot] Bump {update.distribution} to {update.new_version_spec}"
    async with _repo_lock:
        branch_name = f"{BRANCH_PREFIX}/{normalize(update.distribution)}"
        subprocess.check_call(["git", "checkout", "-B", branch_name, "origin/main"])
        with open(update.stub_path / "METADATA.toml", "rb") as f:
            meta = tomlkit.load(f)
        meta["version"] = update.new_version_spec
        with open(update.stub_path / "METADATA.toml", "w", encoding="UTF-8") as f:
            # tomlkit.dump has partially unknown IO type
            tomlkit.dump(meta, f)  # pyright: ignore[reportUnknownMemberType]
        body = get_update_pr_body(update, meta)
        subprocess.check_call(["git", "commit", "--all", "-m", f"{title}\n\n{body}"])
        if action_level <= ActionLevel.local:
            return
        if not latest_commit_is_different_to_last_commit_on_origin(branch_name):
            print(f"No pushing to origin required: origin/{branch_name} exists and requires no changes!")
            return
        somewhat_safe_force_push(branch_name)
        if action_level <= ActionLevel.fork:
            return

    await create_or_update_pull_request(title=title, body=body, branch_name=branch_name, session=session)


async def suggest_typeshed_obsolete(obsolete: Obsolete, session: aiohttp.ClientSession, action_level: ActionLevel) -> None:
    if action_level <= ActionLevel.nothing:
        return
    title = f"[stubsabot] Mark {obsolete.distribution} as obsolete since {obsolete.obsolete_since_version}"
    async with _repo_lock:
        branch_name = f"{BRANCH_PREFIX}/{normalize(obsolete.distribution)}"
        subprocess.check_call(["git", "checkout", "-B", branch_name, "origin/main"])
        with open(obsolete.stub_path / "METADATA.toml", "rb") as f:
            meta = tomlkit.load(f)
        obs_string = tomlkit.string(obsolete.obsolete_since_version)
        obs_string.comment(f"Released on {obsolete.obsolete_since_date.date().isoformat()}")
        meta["obsolete_since"] = obs_string
        with open(obsolete.stub_path / "METADATA.toml", "w", encoding="UTF-8") as f:
            # tomlkit.dump has partially unknown Mapping type
            tomlkit.dump(meta, f)  # pyright: ignore[reportUnknownMemberType]
        body = "\n".join(f"{k}: {v}" for k, v in obsolete.links.items())
        subprocess.check_call(["git", "commit", "--all", "-m", f"{title}\n\n{body}"])
        if action_level <= ActionLevel.local:
            return
        if not latest_commit_is_different_to_last_commit_on_origin(branch_name):
            print(f"No PR required: origin/{branch_name} exists and requires no changes!")
            return
        somewhat_safe_force_push(branch_name)
        if action_level <= ActionLevel.fork:
            return

    await create_or_update_pull_request(title=title, body=body, branch_name=branch_name, session=session)


async def main() -> None:
    assert sys.version_info >= (3, 9)

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--action-level",
        type=ActionLevel.from_cmd_arg,
        default=ActionLevel.everything,
        help="Limit actions performed to achieve dry runs for different levels of dryness",
    )
    parser.add_argument(
        "--action-count-limit",
        type=int,
        default=None,
        help="Limit number of actions performed and the remainder are logged. Useful for testing",
    )
    parser.add_argument("distributions", nargs="*", help="Distributions to update, default = all")
    args = parser.parse_args()

    if args.distributions:
        paths_to_update = [Path("stubs") / distribution for distribution in args.distributions]
    else:
        paths_to_update = list(Path("stubs").iterdir())

    if args.action_level > ActionLevel.nothing:
        subprocess.run(["git", "update-index", "--refresh"], capture_output=True)
        diff_result = subprocess.run(["git", "diff-index", "HEAD", "--name-only"], text=True, capture_output=True)
        if diff_result.returncode:
            print("Unexpected exception!")
            print(diff_result.stdout)
            print(diff_result.stderr)
            sys.exit(diff_result.returncode)
        if diff_result.stdout:
            changed_files = ", ".join(repr(line) for line in diff_result.stdout.split("\n") if line)
            print(f"Cannot run stubsabot, as uncommitted changes are present in {changed_files}!")
            sys.exit(1)

    if args.action_level > ActionLevel.fork:
        if os.environ.get("GITHUB_TOKEN") is None:
            raise ValueError("GITHUB_TOKEN environment variable must be set")

    denylist = {"gdb"}  # gdb is not a pypi distribution

    original_branch = subprocess.run(
        ["git", "branch", "--show-current"], text=True, capture_output=True, check=True
    ).stdout.strip()

    if args.action_level >= ActionLevel.local:
        subprocess.check_call(["git", "fetch", "--prune", "--all"])

    try:
        conn = aiohttp.TCPConnector(limit_per_host=10)
        async with aiohttp.ClientSession(connector=conn) as session:
            tasks = [
                asyncio.create_task(determine_action(stubs_path, session))
                for stubs_path in paths_to_update
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
                    # Redundant, but keeping for extra runtime validation
                    if isinstance(update, Obsolete):  # pyright: ignore[reportUnnecessaryIsInstance]
                        await suggest_typeshed_obsolete(update, session, action_level=args.action_level)
                        continue
                except RemoteConflict as e:
                    print(colored(f"... but ran into {type(e).__qualname__}: {e}", "red"))
                    continue
                raise AssertionError
    finally:
        # if you need to cleanup, try:
        # git branch -D $(git branch --list 'stubsabot/*')
        if args.action_level >= ActionLevel.local and original_branch:
            subprocess.check_call(["git", "checkout", original_branch])


if __name__ == "__main__":
    asyncio.run(main())
