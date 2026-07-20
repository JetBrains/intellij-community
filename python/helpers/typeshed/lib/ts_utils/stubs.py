"""Stub file discovery."""

from functools import cached_property
from pathlib import Path

from ts_utils.paths import STDLIB_PATH, STUBS_PATH, TESTS_DIR, distribution_path
from ts_utils.utils import parse_stdlib_versions_file


class StubFile:
    """Base class for stub files."""

    def __init__(self, path: Path) -> None:
        self.path = path

    def __fspath__(self) -> str:
        return self.path.__fspath__()

    def __str__(self) -> str:
        return str(self.path)

    @cached_property
    def module_name(self) -> str:
        return ".".join(self.module_parts)

    @cached_property
    def module_parts(self) -> tuple[str, ...]:
        raise NotImplementedError


class StdlibStubFile(StubFile):
    """A stdlib stub file."""

    @cached_property
    def module_parts(self) -> tuple[str, ...]:
        relative = self.path.relative_to(STDLIB_PATH)
        parts = list(relative.parts[:-1])
        if relative.name != "__init__.pyi":
            parts.append(relative.stem)
        return tuple(parts)


class ThirdPartyStubFile(StubFile):
    """A third-party stub file."""

    @cached_property
    def upstream_distribution(self) -> str:
        return self.path.relative_to(STUBS_PATH).parts[0]

    @cached_property
    def module_parts(self) -> tuple[str, ...]:
        relative = self.path.relative_to(STUBS_PATH)
        parts = list(relative.parts[1:-1])
        if relative.name != "__init__.pyi":
            parts.append(relative.stem)
        return tuple(parts)


def stdlib_stubs(version: str) -> list[StdlibStubFile]:
    """Return the stdlib stubs available for the requested Python version."""
    module_versions = parse_stdlib_versions_file()
    stubs = (StdlibStubFile(path) for path in path_stubs(STDLIB_PATH))
    return [stub for stub in stubs if module_versions.is_supported(stub.module_name, version)]


def third_party_stubs(distribution: str | None = None) -> list[ThirdPartyStubFile]:
    """Return third-party stubs.

    If distribution is None, return all third-party stubs. Otherwise,
    return only stubs for the given distribution.
    """
    stub_path = distribution_path(distribution) if distribution else STUBS_PATH
    return [ThirdPartyStubFile(path) for path in path_stubs(stub_path)]


def path_stubs(path: Path) -> list[Path]:
    """Return paths to all stub files in a certain path."""
    if path.is_file():
        return [path] if path.suffix == ".pyi" and TESTS_DIR not in path.parts else []
    return sorted(p for p in path.rglob("*.pyi") if TESTS_DIR not in p.parts)
