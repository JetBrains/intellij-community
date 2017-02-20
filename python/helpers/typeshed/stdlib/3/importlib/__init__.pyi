from importlib.abc import Loader
import sys
import types
from typing import Any, Mapping, Optional, Sequence

def __import__(name: str, globals: Mapping[str, Any] = None,
               locals: Mapping[str, Any] = None, fromlist: Sequence[str] = (),
               level: int = 0) -> types.ModuleType: ...

def import_module(name: str, package: str = None) -> types.ModuleType: ...

if sys.version_info >= (3, 3):
    def find_loader(name: str, path: str = None) -> Optional[Loader]: ...

    def invalidate_caches() -> None: ...

if sys.version_info >= (3, 4):
    def reload(module: types.ModuleType) -> types.ModuleType: ...
