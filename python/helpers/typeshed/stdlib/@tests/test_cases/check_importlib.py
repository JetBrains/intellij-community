from __future__ import annotations

import importlib.util
import pathlib
import sys
import zipfile
from collections.abc import Sequence
from importlib.machinery import ModuleSpec
from types import ModuleType
from typing_extensions import Self

if sys.version_info >= (3, 11):
    from importlib.resources.abc import Traversable
else:
    from importlib.abc import Traversable


# Assert that some Path classes are Traversable.
def traverse(t: Traversable) -> None:
    pass


traverse(pathlib.Path())
traverse(zipfile.Path(""))


class MetaFinder:
    @classmethod
    def find_spec(cls, fullname: str, path: Sequence[str] | None, target: ModuleType | None = None) -> ModuleSpec | None:
        return None  # simplified mock for demonstration purposes only


class PathFinder:
    @classmethod
    def path_hook(cls, path_entry: str) -> type[Self]:
        return cls  # simplified mock for demonstration purposes only

    @classmethod
    def find_spec(cls, fullname: str, target: ModuleType | None = None) -> ModuleSpec | None:
        return None  # simplified mock for demonstration purposes only


class Loader:
    @classmethod
    def load_module(cls, fullname: str) -> ModuleType:
        return ModuleType(fullname)


sys.meta_path.append(MetaFinder)
sys.path_hooks.append(PathFinder.path_hook)
importlib.util.spec_from_loader("xxxx42xxxx", Loader)
