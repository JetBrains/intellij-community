from importlib import util
from importlib.abc import Loader
import sys
import types
from typing import Any, Mapping, Optional, Sequence

def __import__(name: str, globals: Optional[Mapping[str, Any]] = None,
               locals: Optional[Mapping[str, Any]] = None,
               fromlist: Sequence[str] = ...,
               level: int = ...) -> types.ModuleType: ...

def import_module(name: str, package: Optional[str] = None) -> types.ModuleType: ...

if sys.version_info >= (3, 3):
    def find_loader(name: str, path: Optional[str] = None) -> Optional[Loader]: ...

    def invalidate_caches() -> None: ...

if sys.version_info >= (3, 4):
    def reload(module: types.ModuleType) -> types.ModuleType: ...
