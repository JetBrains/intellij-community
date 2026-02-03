# This module is made specifically to abstract away those type errors
# pyright: reportUnknownVariableType=false, reportUnknownArgumentType=false

"""Tools to help parse and validate information stored in METADATA.toml files."""

from __future__ import annotations

import datetime
import functools
import re
import sys
import urllib.parse
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Annotated, Any, Final, NamedTuple, final
from typing_extensions import TypeGuard

if sys.version_info >= (3, 11):
    import tomllib
else:
    import tomli as tomllib

import tomlkit
from packaging.requirements import Requirement
from packaging.specifiers import Specifier
from tomlkit.items import String

from .paths import PYPROJECT_PATH, STUBS_PATH, distribution_path

__all__ = [
    "NoSuchStubError",
    "PackageDependencies",
    "StubMetadata",
    "StubtestSettings",
    "get_oldest_supported_python",
    "get_recursive_requirements",
    "read_dependencies",
    "read_metadata",
    "read_stubtest_settings",
]

DEFAULT_STUBTEST_PLATFORMS = ["linux"]

_STUBTEST_PLATFORM_MAPPING: Final = {"linux": "apt_dependencies", "darwin": "brew_dependencies", "win32": "choco_dependencies"}
# Some older websites have a bad pattern of using query params for navigation.
_QUERY_URL_ALLOWLIST = {"sourceware.org"}


def _is_list_of_strings(obj: object) -> TypeGuard[list[str]]:
    return isinstance(obj, list) and all(isinstance(item, str) for item in obj)


def _is_nested_dict(obj: object) -> TypeGuard[dict[str, dict[str, Any]]]:
    return isinstance(obj, dict) and all(isinstance(k, str) and isinstance(v, dict) for k, v in obj.items())


@functools.cache
def get_oldest_supported_python() -> str:
    with PYPROJECT_PATH.open("rb") as config:
        val = tomllib.load(config)["tool"]["typeshed"]["oldest_supported_python"]
    assert type(val) is str
    return val


def metadata_path(distribution: str) -> Path:
    """Return the path to the METADATA.toml file of a third-party distribution."""
    return distribution_path(distribution) / "METADATA.toml"


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
    supported_platforms: list[str] | None  # None means all platforms
    ci_platforms: list[str]
    stubtest_requirements: list[str]
    mypy_plugins: list[str]
    mypy_plugins_config: dict[str, dict[str, Any]]

    def system_requirements_for_platform(self, platform: str) -> list[str]:
        assert platform in _STUBTEST_PLATFORM_MAPPING, f"Unrecognised platform {platform!r}"
        ret = getattr(self, _STUBTEST_PLATFORM_MAPPING[platform])
        assert _is_list_of_strings(ret)
        return ret


@functools.cache
def read_stubtest_settings(distribution: str) -> StubtestSettings:
    """Return an object describing the stubtest settings for a single stubs distribution."""
    with metadata_path(distribution).open("rb") as f:
        data: dict[str, object] = tomllib.load(f).get("tool", {}).get("stubtest", {})

    skip: object = data.get("skip", False)
    apt_dependencies: object = data.get("apt_dependencies", [])
    brew_dependencies: object = data.get("brew_dependencies", [])
    choco_dependencies: object = data.get("choco_dependencies", [])
    extras: object = data.get("extras", [])
    ignore_missing_stub: object = data.get("ignore_missing_stub", False)
    supported_platforms: object = data.get("supported_platforms")
    ci_platforms: object = data.get("ci_platforms", DEFAULT_STUBTEST_PLATFORMS)
    stubtest_requirements: object = data.get("stubtest_requirements", [])
    mypy_plugins: object = data.get("mypy_plugins", [])
    mypy_plugins_config: object = data.get("mypy_plugins_config", {})

    assert type(skip) is bool
    assert type(ignore_missing_stub) is bool

    # It doesn't work for type-narrowing if we use a for loop here...
    assert supported_platforms is None or _is_list_of_strings(supported_platforms)
    assert _is_list_of_strings(ci_platforms)
    assert _is_list_of_strings(apt_dependencies)
    assert _is_list_of_strings(brew_dependencies)
    assert _is_list_of_strings(choco_dependencies)
    assert _is_list_of_strings(extras)
    assert _is_list_of_strings(stubtest_requirements)
    assert _is_list_of_strings(mypy_plugins)
    assert _is_nested_dict(mypy_plugins_config)

    unrecognised_platforms = set(ci_platforms) - _STUBTEST_PLATFORM_MAPPING.keys()
    assert not unrecognised_platforms, f"Unrecognised ci_platforms specified for {distribution!r}: {unrecognised_platforms}"

    if supported_platforms is not None:
        assert set(ci_platforms).issubset(
            supported_platforms
        ), f"ci_platforms must be a subset of supported_platforms for {distribution!r}"

    for platform, dep_key in _STUBTEST_PLATFORM_MAPPING.items():
        if platform not in ci_platforms:
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
        supported_platforms=supported_platforms,
        ci_platforms=ci_platforms,
        stubtest_requirements=stubtest_requirements,
        mypy_plugins=mypy_plugins,
        mypy_plugins_config=mypy_plugins_config,
    )


@final
@dataclass(frozen=True)
class ObsoleteMetadata:
    since_version: Annotated[str, "A string representing a specific version"]
    since_date: Annotated[datetime.date, "A date when the package became obsolete"]


@final
@dataclass(frozen=True)
class StubMetadata:
    """The metadata for a single stubs distribution.

    Don't construct instances directly; use the `read_metadata` function.
    """

    distribution: Annotated[str, "The name of the distribution on PyPI"]
    version_spec: Annotated[Specifier, "Upstream versions that the stubs are compatible with"]
    requires: Annotated[list[Requirement], "The parsed requirements as listed in METADATA.toml"]
    extra_description: str | None
    stub_distribution: Annotated[str, "The name under which the distribution is uploaded to PyPI"]
    upstream_repository: Annotated[str, "The URL of the upstream repository"] | None
    obsolete: Annotated[ObsoleteMetadata, "Metadata indicating when the stubs package became obsolete"] | None
    no_longer_updated: bool
    uploaded_to_pypi: Annotated[bool, "Whether or not a distribution is uploaded to PyPI"]
    partial_stub: Annotated[bool, "Whether this is a partial type stub package as per PEP 561."]
    stubtest_settings: StubtestSettings
    requires_python: Annotated[Specifier, "Versions of Python supported by the stub package"]

    @property
    def is_obsolete(self) -> bool:
        return self.obsolete is not None


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
        "mypy-tests",
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
        "supported_platforms",
        "ci_platforms",
        "stubtest_requirements",
        "mypy_plugins",
        "mypy_plugins_config",
    }
}
_DIST_NAME_RE: Final = re.compile(r"^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$", re.IGNORECASE)


class NoSuchStubError(ValueError):
    """Raise NoSuchStubError to indicate that a stubs/{distribution} directory doesn't exist."""


@functools.cache
def read_metadata(distribution: str) -> StubMetadata:
    """Return an object describing the metadata of a stub as given in the METADATA.toml file.

    This function does some basic validation,
    but does no parsing, transforming or normalization of the metadata.
    Use `read_dependencies` if you need to parse the dependencies
    given in the `requires` field, for example.
    """
    try:
        with metadata_path(distribution).open("rb") as f:
            data = tomlkit.load(f)
    except FileNotFoundError:
        raise NoSuchStubError(f"Typeshed has no stubs for {distribution!r}!") from None

    unknown_metadata_fields = data.keys() - _KNOWN_METADATA_FIELDS
    assert not unknown_metadata_fields, f"Unexpected keys in METADATA.toml for {distribution!r}: {unknown_metadata_fields}"

    assert "version" in data, f"Missing 'version' field in METADATA.toml for {distribution!r}"
    version: object = data.get("version")  # pyright: ignore[reportUnknownMemberType]
    assert isinstance(version, str) and len(version) > 0, f"Invalid 'version' field in METADATA.toml for {distribution!r}"
    # Check that the version spec parses
    if version[0].isdigit():
        version = f"=={version}"
    version_spec = Specifier(version)
    assert version_spec.operator in {"==", "~="}, f"Invalid 'version' field in METADATA.toml for {distribution!r}"

    requires_s: object = data.get("requires", [])  # pyright: ignore[reportUnknownMemberType]
    assert isinstance(requires_s, list)
    requires = [parse_requires(distribution, req) for req in requires_s]

    extra_description: object = data.get("extra_description")  # pyright: ignore[reportUnknownMemberType]
    assert isinstance(extra_description, (str, type(None)))

    if "stub_distribution" in data:
        stub_distribution = data["stub_distribution"]
        assert isinstance(stub_distribution, str)
        assert _DIST_NAME_RE.fullmatch(stub_distribution), f"Invalid 'stub_distribution' value for {distribution!r}"
    else:
        stub_distribution = f"types-{distribution}"

    upstream_repository: object = data.get("upstream_repository")  # pyright: ignore[reportUnknownMemberType]
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

    obsolete_since: object = data.get("obsolete_since")  # pyright: ignore[reportUnknownMemberType]
    assert isinstance(obsolete_since, (String, type(None)))
    if obsolete_since:
        comment = obsolete_since.trivia.comment
        since_date_string = comment.removeprefix("# Released on ")
        since_date = datetime.date.fromisoformat(since_date_string)
        obsolete = ObsoleteMetadata(since_version=obsolete_since, since_date=since_date)
    else:
        obsolete = None
    no_longer_updated: object = data.get("no_longer_updated", False)  # pyright: ignore[reportUnknownMemberType]
    assert type(no_longer_updated) is bool
    uploaded_to_pypi: object = data.get("upload", True)  # pyright: ignore[reportUnknownMemberType]
    assert type(uploaded_to_pypi) is bool
    partial_stub: object = data.get("partial_stub", True)  # pyright: ignore[reportUnknownMemberType]
    assert type(partial_stub) is bool
    requires_python_str: object = data.get("requires_python")  # pyright: ignore[reportUnknownMemberType]
    oldest_supported_python = get_oldest_supported_python()
    oldest_supported_python_specifier = Specifier(f">={oldest_supported_python}")
    if requires_python_str is None:
        requires_python = oldest_supported_python_specifier
    else:
        assert isinstance(requires_python_str, str)
        requires_python = Specifier(requires_python_str)
        assert requires_python != oldest_supported_python_specifier, f'requires_python="{requires_python}" is redundant'
        # Check minimum Python version is not less than the oldest version of Python supported by typeshed
        assert oldest_supported_python_specifier.contains(
            requires_python.version
        ), f"'requires_python' contains versions lower than typeshed's oldest supported Python ({oldest_supported_python})"
        assert requires_python.operator == ">=", "'requires_python' should be a minimum version specifier, use '>=3.x'"

    empty_tools: dict[object, object] = {}
    tools_settings: object = data.get("tool", empty_tools)  # pyright: ignore[reportUnknownMemberType]
    assert isinstance(tools_settings, dict)
    assert tools_settings.keys() <= _KNOWN_METADATA_TOOL_FIELDS.keys(), f"Unrecognised tool for {distribution!r}"
    for tool, tk in _KNOWN_METADATA_TOOL_FIELDS.items():
        settings_for_tool: object = tools_settings.get(tool, {})  # pyright: ignore[reportUnknownMemberType]
        assert isinstance(settings_for_tool, dict)
        for key in settings_for_tool:
            assert key in tk, f"Unrecognised {tool} key {key!r} for {distribution!r}"

    return StubMetadata(
        distribution=distribution,
        version_spec=version_spec,
        requires=requires,
        extra_description=extra_description,
        stub_distribution=stub_distribution,
        upstream_repository=upstream_repository,
        obsolete=obsolete,
        no_longer_updated=no_longer_updated,
        uploaded_to_pypi=uploaded_to_pypi,
        partial_stub=partial_stub,
        stubtest_settings=read_stubtest_settings(distribution),
        requires_python=requires_python,
    )


def update_metadata(distribution: str, **new_values: object) -> tomlkit.TOMLDocument:
    """Update a distribution's METADATA.toml.

    Return the updated TOML dictionary for use without having to open the file separately.
    """
    path = metadata_path(distribution)
    try:
        with path.open("rb") as file:
            data = tomlkit.load(file)
    except FileNotFoundError:
        raise NoSuchStubError(f"Typeshed has no stubs for {distribution!r}!") from None
    data.update(new_values)  # pyright: ignore[reportUnknownMemberType] # tomlkit.TOMLDocument.update is partially typed
    with path.open("w", encoding="UTF-8") as file:
        tomlkit.dump(data, file)  # pyright: ignore[reportUnknownMemberType] # tomlkit.dump has partially unknown Mapping type
    return data


def parse_requires(distribution: str, req: object) -> Requirement:
    assert isinstance(req, str), f"Invalid requirement {req!r} for {distribution!r}"
    return Requirement(req)


class PackageDependencies(NamedTuple):
    typeshed_pkgs: tuple[Requirement, ...]
    external_pkgs: tuple[Requirement, ...]


@functools.cache
def get_pypi_name_to_typeshed_name_mapping() -> Mapping[str, str]:
    return {read_metadata(stub_dir.name).stub_distribution: stub_dir.name for stub_dir in STUBS_PATH.iterdir()}


@functools.cache
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
    typeshed: list[Requirement] = []
    external: list[Requirement] = []
    for dependency in read_metadata(distribution).requires:
        if dependency.name in pypi_name_to_typeshed_name_mapping:
            req = Requirement(str(dependency))  # copy the requirement
            req.name = pypi_name_to_typeshed_name_mapping[dependency.name]
            typeshed.append(req)
        else:
            external.append(dependency)
    return PackageDependencies(tuple(typeshed), tuple(external))


@functools.cache
def get_recursive_requirements(package_name: str) -> PackageDependencies:
    """Recursively gather dependencies for a single stubs package.

    For example, if the stubs for `caldav`
    declare a dependency on typeshed's stubs for `requests`,
    and the stubs for requests declare a dependency on typeshed's stubs for `urllib3`,
    `get_recursive_requirements("caldav")` will determine that the stubs for `caldav`
    have both `requests` and `urllib3` as typeshed-internal dependencies.
    """
    typeshed: set[Requirement] = set()
    external: set[Requirement] = set()
    non_recursive_requirements = read_dependencies(package_name)
    typeshed.update(non_recursive_requirements.typeshed_pkgs)
    external.update(non_recursive_requirements.external_pkgs)
    for pkg in non_recursive_requirements.typeshed_pkgs:
        reqs = get_recursive_requirements(pkg.name)
        typeshed.update(reqs.typeshed_pkgs)
        external.update(reqs.external_pkgs)
    return PackageDependencies(tuple(typeshed), tuple(external))
