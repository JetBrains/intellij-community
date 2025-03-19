# This module is made specifically to abstract away those type errors
# pyright: reportUnknownVariableType=false, reportUnknownArgumentType=false

"""Tools to help parse and validate information stored in METADATA.toml files."""

from __future__ import annotations

import os
import re
import urllib.parse
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Final, NamedTuple, final
from typing_extensions import Annotated, TypeGuard

import tomli
from packaging.requirements import Requirement
from packaging.specifiers import Specifier
from packaging.version import Version

from _utils import cache

__all__ = [
    "NoSuchStubError",
    "StubMetadata",
    "PackageDependencies",
    "StubtestSettings",
    "get_recursive_requirements",
    "read_dependencies",
    "read_metadata",
    "read_stubtest_settings",
]


_STUBTEST_PLATFORM_MAPPING: Final = {"linux": "apt_dependencies", "darwin": "brew_dependencies", "win32": "choco_dependencies"}
# Some older websites have a bad pattern of using query params for navigation.
_QUERY_URL_ALLOWLIST = {"sourceware.org"}


def _is_list_of_strings(obj: object) -> TypeGuard[list[str]]:
    return isinstance(obj, list) and all(isinstance(item, str) for item in obj)


@cache
def _get_oldest_supported_python() -> str:
    with open("pyproject.toml", "rb") as config:
        val = tomli.load(config)["tool"]["typeshed"]["oldest_supported_python"]
    assert type(val) is str
    return val


@final
@dataclass(frozen=True)
class StubtestSettings:
    """The stubtest settings for a single stubs distribution.

    Don't construct instances directly; use the `read_stubtest_settings` function.
    """

    skip: bool
    apt_dependencies: list[str]
    brew_dependencies: list[str]
    choco_dependencies: list[str]
    extras: list[str]
    ignore_missing_stub: bool
    platforms: list[str]
    stubtest_requirements: list[str]

    def system_requirements_for_platform(self, platform: str) -> list[str]:
        assert platform in _STUBTEST_PLATFORM_MAPPING, f"Unrecognised platform {platform!r}"
        ret = getattr(self, _STUBTEST_PLATFORM_MAPPING[platform])
        assert _is_list_of_strings(ret)
        return ret


@cache
def read_stubtest_settings(distribution: str) -> StubtestSettings:
    """Return an object describing the stubtest settings for a single stubs distribution."""
    with Path("stubs", distribution, "METADATA.toml").open("rb") as f:
        data: dict[str, object] = tomli.load(f).get("tool", {}).get("stubtest", {})

    skip: object = data.get("skip", False)
    apt_dependencies: object = data.get("apt_dependencies", [])
    brew_dependencies: object = data.get("brew_dependencies", [])
    choco_dependencies: object = data.get("choco_dependencies", [])
    extras: object = data.get("extras", [])
    ignore_missing_stub: object = data.get("ignore_missing_stub", False)
    specified_platforms: object = data.get("platforms", ["linux"])
    stubtest_requirements: object = data.get("stubtest_requirements", [])

    assert type(skip) is bool
    assert type(ignore_missing_stub) is bool

    # It doesn't work for type-narrowing if we use a for loop here...
    assert _is_list_of_strings(specified_platforms)
    assert _is_list_of_strings(apt_dependencies)
    assert _is_list_of_strings(brew_dependencies)
    assert _is_list_of_strings(choco_dependencies)
    assert _is_list_of_strings(extras)
    assert _is_list_of_strings(stubtest_requirements)

    unrecognised_platforms = set(specified_platforms) - _STUBTEST_PLATFORM_MAPPING.keys()
    assert not unrecognised_platforms, f"Unrecognised platforms specified for {distribution!r}: {unrecognised_platforms}"

    for platform, dep_key in _STUBTEST_PLATFORM_MAPPING.items():
        if platform not in specified_platforms:
            assert dep_key not in data, (
                f"Stubtest is not run on {platform} in CI for {distribution!r}, "
                f"but {dep_key!r} are specified in METADATA.toml"
            )

    return StubtestSettings(
        skip=skip,
        apt_dependencies=apt_dependencies,
        brew_dependencies=brew_dependencies,
        choco_dependencies=choco_dependencies,
        extras=extras,
        ignore_missing_stub=ignore_missing_stub,
        platforms=specified_platforms,
        stubtest_requirements=stubtest_requirements,
    )


@final
@dataclass(frozen=True)
class StubMetadata:
    """The metadata for a single stubs distribution.

    Don't construct instances directly; use the `read_metadata` function.
    """

    version: str
    requires: Annotated[list[str], "The raw requirements as listed in METADATA.toml"]
    extra_description: str | None
    stub_distribution: Annotated[str, "The name under which the distribution is uploaded to PyPI"]
    upstream_repository: Annotated[str, "The URL of the upstream repository"] | None
    obsolete_since: Annotated[str, "A string representing a specific version"] | None
    no_longer_updated: bool
    uploaded_to_pypi: Annotated[bool, "Whether or not a distribution is uploaded to PyPI"]
    partial_stub: Annotated[bool, "Whether this is a partial type stub package as per PEP 561."]
    stubtest_settings: StubtestSettings
    requires_python: Annotated[Specifier, "Versions of Python supported by the stub package"]


_KNOWN_METADATA_FIELDS: Final = frozenset(
    {
        "version",
        "requires",
        "extra_description",
        "stub_distribution",
        "upstream_repository",
        "obsolete_since",
        "no_longer_updated",
        "upload",
        "tool",
        "partial_stub",
        "requires_python",
    }
)
_KNOWN_METADATA_TOOL_FIELDS: Final = {
    "stubtest": {
        "skip",
        "apt_dependencies",
        "brew_dependencies",
        "choco_dependencies",
        "extras",
        "ignore_missing_stub",
        "platforms",
        "stubtest_requirements",
    }
}
_DIST_NAME_RE: Final = re.compile(r"^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$", re.IGNORECASE)


class NoSuchStubError(ValueError):
    """Raise NoSuchStubError to indicate that a stubs/{distribution} directory doesn't exist"""


@cache
def read_metadata(distribution: str) -> StubMetadata:
    """Return an object describing the metadata of a stub as given in the METADATA.toml file.

    This function does some basic validation,
    but does no parsing, transforming or normalization of the metadata.
    Use `read_dependencies` if you need to parse the dependencies
    given in the `requires` field, for example.
    """
    try:
        with Path("stubs", distribution, "METADATA.toml").open("rb") as f:
            data: dict[str, object] = tomli.load(f)
    except FileNotFoundError:
        raise NoSuchStubError(f"Typeshed has no stubs for {distribution!r}!") from None

    unknown_metadata_fields = data.keys() - _KNOWN_METADATA_FIELDS
    assert not unknown_metadata_fields, f"Unexpected keys in METADATA.toml for {distribution!r}: {unknown_metadata_fields}"

    assert "version" in data, f"Missing 'version' field in METADATA.toml for {distribution!r}"
    version = data["version"]
    assert isinstance(version, str)
    # Check that the version parses
    Version(version[:-2] if version.endswith(".*") else version)

    requires: object = data.get("requires", [])
    assert isinstance(requires, list)
    for req in requires:
        assert isinstance(req, str), f"Invalid requirement {req!r} for {distribution!r}"
        for space in " \t\n":
            assert space not in req, f"For consistency, requirement should not have whitespace: {req!r}"
        # Check that the requirement parses
        Requirement(req)

    extra_description: object = data.get("extra_description")
    assert isinstance(extra_description, (str, type(None)))

    if "stub_distribution" in data:
        stub_distribution = data["stub_distribution"]
        assert isinstance(stub_distribution, str)
        assert _DIST_NAME_RE.fullmatch(stub_distribution), f"Invalid 'stub_distribution' value for {distribution!r}"
    else:
        stub_distribution = f"types-{distribution}"

    upstream_repository: object = data.get("upstream_repository")
    assert isinstance(upstream_repository, (str, type(None)))
    if isinstance(upstream_repository, str):
        parsed_url = urllib.parse.urlsplit(upstream_repository)
        assert parsed_url.scheme == "https", f"{distribution}: URLs in the upstream_repository field should use https"
        no_www_please = (
            f"{distribution}: `World Wide Web` subdomain (`www.`) should be removed from URLs in the upstream_repository field"
        )
        assert not parsed_url.netloc.startswith("www."), no_www_please
        no_query_params_please = (
            f"{distribution}: Query params (`?`) should be removed from URLs in the upstream_repository field"
        )
        assert parsed_url.hostname in _QUERY_URL_ALLOWLIST or (not parsed_url.query), no_query_params_please
        no_fragments_please = f"{distribution}: Fragments (`#`) should be removed from URLs in the upstream_repository field"
        assert not parsed_url.fragment, no_fragments_please
        if parsed_url.netloc == "github.com":
            cleaned_url_path = parsed_url.path.strip("/")
            num_url_path_parts = len(Path(cleaned_url_path).parts)
            bad_github_url_msg = (
                f"Invalid upstream_repository for {distribution!r}: "
                "URLs for GitHub repositories always have two parts in their paths"
            )
            assert num_url_path_parts == 2, bad_github_url_msg

    obsolete_since: object = data.get("obsolete_since")
    assert isinstance(obsolete_since, (str, type(None)))
    no_longer_updated: object = data.get("no_longer_updated", False)
    assert type(no_longer_updated) is bool
    uploaded_to_pypi: object = data.get("upload", True)
    assert type(uploaded_to_pypi) is bool
    partial_stub: object = data.get("partial_stub", True)
    assert type(partial_stub) is bool
    requires_python_str: object = data.get("requires_python")
    oldest_supported_python = _get_oldest_supported_python()
    oldest_supported_python_specifier = Specifier(f">={oldest_supported_python}")
    if requires_python_str is None:
        requires_python = oldest_supported_python_specifier
    else:
        assert type(requires_python_str) is str
        requires_python = Specifier(requires_python_str)
        assert requires_python != oldest_supported_python_specifier, f'requires_python="{requires_python}" is redundant'
        # Check minimum Python version is not less than the oldest version of Python supported by typeshed
        assert oldest_supported_python_specifier.contains(
            requires_python.version
        ), f"'requires_python' contains versions lower than typeshed's oldest supported Python ({oldest_supported_python})"
        assert requires_python.operator == ">=", "'requires_python' should be a minimum version specifier, use '>=3.x'"

    empty_tools: dict[object, object] = {}
    tools_settings: object = data.get("tool", empty_tools)
    assert isinstance(tools_settings, dict)
    assert tools_settings.keys() <= _KNOWN_METADATA_TOOL_FIELDS.keys(), f"Unrecognised tool for {distribution!r}"
    for tool, tk in _KNOWN_METADATA_TOOL_FIELDS.items():
        settings_for_tool: object = tools_settings.get(tool, {})  # pyright: ignore[reportUnknownMemberType]
        assert isinstance(settings_for_tool, dict)
        for key in settings_for_tool:
            assert key in tk, f"Unrecognised {tool} key {key!r} for {distribution!r}"

    return StubMetadata(
        version=version,
        requires=requires,
        extra_description=extra_description,
        stub_distribution=stub_distribution,
        upstream_repository=upstream_repository,
        obsolete_since=obsolete_since,
        no_longer_updated=no_longer_updated,
        uploaded_to_pypi=uploaded_to_pypi,
        partial_stub=partial_stub,
        stubtest_settings=read_stubtest_settings(distribution),
        requires_python=requires_python,
    )


class PackageDependencies(NamedTuple):
    typeshed_pkgs: tuple[str, ...]
    external_pkgs: tuple[str, ...]


@cache
def get_pypi_name_to_typeshed_name_mapping() -> Mapping[str, str]:
    return {read_metadata(typeshed_name).stub_distribution: typeshed_name for typeshed_name in os.listdir("stubs")}


@cache
def read_dependencies(distribution: str) -> PackageDependencies:
    """Read the dependencies listed in a METADATA.toml file for a stubs package.

    Once the dependencies have been read,
    determine which dependencies are typeshed-internal dependencies,
    and which dependencies are external (non-types) dependencies.
    For typeshed dependencies, translate the "dependency name" into the "package name";
    for external dependencies, leave them as they are in the METADATA.toml file.

    Note that this function may consider things to be typeshed stubs
    even if they haven't yet been uploaded to PyPI.
    If a typeshed stub is removed, this function will consider it to be an external dependency.
    """
    pypi_name_to_typeshed_name_mapping = get_pypi_name_to_typeshed_name_mapping()
    typeshed: list[str] = []
    external: list[str] = []
    for dependency in read_metadata(distribution).requires:
        maybe_typeshed_dependency = Requirement(dependency).name
        if maybe_typeshed_dependency in pypi_name_to_typeshed_name_mapping:
            typeshed.append(pypi_name_to_typeshed_name_mapping[maybe_typeshed_dependency])
        else:
            # convert to Requirement and then back to str
            # to make sure that the requirements all have a normalised string representation
            # (This will also catch any malformed requirements early)
            external.append(str(Requirement(dependency)))
    return PackageDependencies(tuple(typeshed), tuple(external))


@cache
def get_recursive_requirements(package_name: str) -> PackageDependencies:
    """Recursively gather dependencies for a single stubs package.

    For example, if the stubs for `caldav`
    declare a dependency on typeshed's stubs for `requests`,
    and the stubs for requests declare a dependency on typeshed's stubs for `urllib3`,
    `get_recursive_requirements("caldav")` will determine that the stubs for `caldav`
    have both `requests` and `urllib3` as typeshed-internal dependencies.
    """
    typeshed: set[str] = set()
    external: set[str] = set()
    non_recursive_requirements = read_dependencies(package_name)
    typeshed.update(non_recursive_requirements.typeshed_pkgs)
    external.update(non_recursive_requirements.external_pkgs)
    for pkg in non_recursive_requirements.typeshed_pkgs:
        reqs = get_recursive_requirements(pkg)
        typeshed.update(reqs.typeshed_pkgs)
        external.update(reqs.external_pkgs)
    return PackageDependencies(tuple(sorted(typeshed)), tuple(sorted(external)))
