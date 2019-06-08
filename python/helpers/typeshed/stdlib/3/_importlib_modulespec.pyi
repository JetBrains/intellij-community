# ModuleSpec, ModuleType, Loader are part of a dependency cycle.
# They are officially defined/exported in other places:
#
# - ModuleType in types
# - Loader in importlib.abc
# - ModuleSpec in importlib.machinery (3.4 and later only)
#
# _Loader is the PEP-451-defined interface for a loader type/object.

from abc import ABCMeta
import sys
from typing import Any, Dict, List, Optional, Protocol

class _Loader(Protocol):
    def load_module(self, fullname: str) -> ModuleType: ...

class ModuleSpec:
    def __init__(self, name: str, loader: Optional[Loader], *,
                 origin: Optional[str] = ..., loader_state: Any = ...,
                 is_package: Optional[bool] = ...) -> None: ...
    name = ...  # type: str
    loader = ...  # type: Optional[_Loader]
    origin = ...  # type: Optional[str]
    submodule_search_locations = ...  # type: Optional[List[str]]
    loader_state = ...  # type: Any
    cached = ...  # type: Optional[str]
    parent = ...  # type: Optional[str]
    has_location = ...  # type: bool

class ModuleType:
    __name__ = ...  # type: str
    __file__ = ...  # type: str
    __dict__ = ...  # type: Dict[str, Any]
    __loader__ = ...  # type: Optional[_Loader]
    __package__ = ...  # type: Optional[str]
    __spec__ = ...  # type: Optional[ModuleSpec]
    def __init__(self, name: str, doc: Optional[str] = ...) -> None: ...

class Loader(metaclass=ABCMeta):
    def load_module(self, fullname: str) -> ModuleType: ...
    def module_repr(self, module: ModuleType) -> str: ...
    def create_module(self, spec: ModuleSpec) -> Optional[ModuleType]: ...
    # Not defined on the actual class for backwards-compatibility reasons,
    # but expected in new code.
    def exec_module(self, module: ModuleType) -> None: ...
