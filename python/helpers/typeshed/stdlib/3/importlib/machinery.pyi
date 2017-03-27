import importlib.abc
import sys
import types
from typing import Any, Callable, List, Optional, Sequence, Tuple, Union

# ModuleSpec is exported from this module, but for circular import
# reasons exists in its own stub file (with Loader and ModuleType).
if sys.version_info >= (3, 4):
    from _importlib_modulespec import ModuleSpec as ModuleSpec  # Exported

if sys.version_info >= (3, 3):
    class BuiltinImporter(importlib.abc.MetaPathFinder,
                          importlib.abc.InspectLoader):
        # MetaPathFinder
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...
        if sys.version_info >= (3, 4):
            @classmethod
            def find_spec(cls, fullname: str,
                          path: Optional[Sequence[importlib.abc._Path]],
                          target: types.ModuleType = None) -> Optional[ModuleSpec]:
                ...
        # InspectLoader
        @classmethod
        def is_package(cls, fullname: str) -> bool: ...
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        @classmethod
        def get_code(cls, fullname: str) -> None: ...  # type: ignore
        @classmethod
        def get_source(cls, fullname: str) -> None: ...  # type: ignore
        # Loader
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        if sys.version_info >= (3, 3):
            @staticmethod
            def module_repr(module: types.ModuleType) -> str: ...  # type: ignore
        if sys.version_info >= (3, 4):
            @classmethod
            def create_module(cls, spec: ModuleSpec) -> Optional[types.ModuleType]: ...
            @classmethod
            def exec_module(cls, module: types.ModuleType) -> None: ...
else:
    class BuiltinImporter(importlib.abc.InspectLoader):
        # MetaPathFinder
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...
        # InspectLoader
        @classmethod
        def is_package(cls, fullname: str) -> bool: ...
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        @classmethod
        def get_code(cls, fullname: str) -> None: ...  # type: ignore
        @classmethod
        def get_source(cls, fullname: str) -> None: ...  # type: ignore
        # Loader
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...

if sys.version_info >= (3, 3):
    class FrozenImporter(importlib.abc.MetaPathFinder, importlib.abc.InspectLoader):
        # MetaPathFinder
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...
        if sys.version_info >= (3, 4):
            @classmethod
            def find_spec(cls, fullname: str,
                          path: Optional[Sequence[importlib.abc._Path]],
                          target: types.ModuleType = None) -> Optional[ModuleSpec]:
                ...
        # InspectLoader
        @classmethod
        def is_package(cls, fullname: str) -> bool: ...
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        @classmethod
        def get_code(cls, fullname: str) -> None: ...  # type: ignore
        @classmethod
        def get_source(cls, fullname: str) -> None: ...  # type: ignore
        # Loader
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        if sys.version_info >= (3, 3):
            @staticmethod
            def module_repr(module: types.ModuleType) -> str: ...  # type: ignore
        if sys.version_info >= (3, 4):
            @classmethod
            def create_module(cls, spec: ModuleSpec) -> Optional[types.ModuleType]:
                ...
            @staticmethod
            def exec_module(module: types.ModuleType) -> None: ...  # type: ignore
else:
    class FrozenImporter(importlib.abc.InspectLoader):
        # MetaPathFinder
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...
        # InspectLoader
        @classmethod
        def is_package(cls, fullname: str) -> bool: ...
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...
        @classmethod
        def get_code(cls, fullname: str) -> None: ...  # type: ignore
        @classmethod
        def get_source(cls, fullname: str) -> None: ...  # type: ignore
        # Loader
        @classmethod
        def load_module(cls, fullname: str) -> types.ModuleType: ...

if sys.version_info >= (3, 3):
    class WindowsRegisteryFinder(importlib.abc.MetaPathFinder):
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...
        if sys.version_info >= (3, 4):
            @classmethod
            def find_spec(cls, fullname: str,
                          path: Optional[Sequence[importlib.abc._Path]],
                          target: types.ModuleType = None) -> Optional[ModuleSpec]:
                ...
else:
    class WindowsRegisteryFinder:
        @classmethod
        def find_module(
            cls, fullname: str,
            path: Optional[Sequence[importlib.abc._Path]]
        ) -> Optional[importlib.abc.Loader]:
            ...

if sys.version_info >= (3, 3):
    class PathFinder(importlib.abc.MetaPathFinder): ...
else:
    class PathFinder: ...

if sys.version_info >= (3, 3):
    SOURCE_SUFFIXES = ...  # type: List[str]
    DEBUG_BYTECODE_SUFFIXES = ...  # type: List[str]
    OPTIMIZED_BYTECODE_SUFFIXES = ...  # type: List[str]
    BYTECODE_SUFFIXES = ...  # type: List[str]
    EXTENSION_SUFFIXES = ...  # type: List[str]

    def all_suffixes() -> List[str]: ...

    class FileFinder(importlib.abc.PathEntryFinder):
        path = ...  # type: str
        def __init__(
            self, path: str,
            *loader_details: Tuple[importlib.abc.Loader, List[str]]
        ) -> None: ...
        @classmethod
        def path_hook(
            *loader_details: Tuple[importlib.abc.Loader, List[str]]
        ) -> Callable[[str], importlib.abc.PathEntryFinder]: ...

    class SourceFileLoader(importlib.abc.FileLoader,
                           importlib.abc.SourceLoader):
        ...

    class SourcelessFileLoader(importlib.abc.FileLoader,
                               importlib.abc.SourceLoader):
        ...

    class ExtensionFileLoader(importlib.abc.ExecutionLoader):
        def get_filename(self, fullname: str) -> importlib.abc._Path: ...
        def get_source(self, fullname: str) -> None: ...  # type: ignore
