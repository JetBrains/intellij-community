# https://pyinstaller.org/en/stable/hooks.html#module-PyInstaller.utils.hooks.conda

import sys
from _typeshed import StrOrBytesPath
from collections.abc import Iterable
from pathlib import Path, PurePosixPath
from typing_extensions import Final, TypedDict

if sys.version_info >= (3, 8):
    from importlib.metadata import PackagePath as _PackagePath
else:
    # Same as importlib_metadata.PackagePath
    class _PackagePath(PurePosixPath):
        def read_text(self, encoding: str = "utf-8") -> str: ...
        def read_binary(self) -> str: ...
        def locate(self) -> Path: ...

CONDA_ROOT: Final[Path]
CONDA_META_DIR: Final[Path]
PYTHONPATH_PREFIXES: Final[list[Path]]

class _RawDict(TypedDict):
    name: str
    version: str
    files: list[StrOrBytesPath]
    depends: list[str]

class Distribution:
    raw: _RawDict
    name: str
    version: str
    files: list[PackagePath]
    dependencies: list[str]
    packages: list[str]
    def __init__(self, json_path: str) -> None: ...
    @classmethod
    def from_name(cls, name: str) -> Distribution: ...
    @classmethod
    def from_package_name(cls, name: str) -> Distribution: ...

# distribution and package_distribution are meant to be used and are not internal helpers
distribution = Distribution.from_name
package_distribution = Distribution.from_package_name

class PackagePath(_PackagePath):
    def locate(self) -> Path: ...

def walk_dependency_tree(initial: str, excludes: Iterable[str] | None = None) -> dict[str, Distribution]: ...
def requires(name: str, strip_versions: bool = False) -> list[str]: ...
def files(name: str, dependencies: bool = False, excludes: Iterable[str] | None = None) -> list[PackagePath]: ...
def collect_dynamic_libs(
    name: str, dest: str = ".", dependencies: bool = True, excludes: Iterable[str] | None = None
) -> list[tuple[str, str]]: ...

distributions: dict[str, Distribution]
distributions_by_package: dict[str | None, Distribution]
