from _typeshed import Incomplete
from typing import Any

from pyVmomi.vim import ManagedEntity
from pyVmomi.vim.view import ContainerView
from pyVmomi.vmodl import DynamicProperty

class PropertyCollector:
    class PropertySpec:
        def __init__(self, *, all: bool = ..., type: type[ManagedEntity] = ..., pathSet: list[str] = ...) -> None: ...
        all: bool
        type: type[ManagedEntity]
        pathSet: list[str]

    class TraversalSpec:
        def __init__(
            self, *, path: str = ..., skip: bool = ..., type: type[ContainerView] = ..., **kwargs: Incomplete
        ) -> None: ...
        path: str
        skip: bool
        type: type[ContainerView]
        def __getattr__(self, name: str) -> Incomplete: ...

    class RetrieveOptions:
        def __init__(self, *, maxObjects: int | None = ...) -> None: ...
        maxObjects: int | None

    class ObjectSpec:
        def __init__(
            self, *, skip: bool = ..., selectSet: list[PropertyCollector.TraversalSpec] = ..., obj: Any = ...
        ) -> None: ...
        skip: bool
        selectSet: list[PropertyCollector.TraversalSpec]
        obj: Any

    class FilterSpec:
        def __init__(
            self,
            *,
            propSet: list[PropertyCollector.PropertySpec] = ...,
            objectSet: list[PropertyCollector.ObjectSpec] = ...,
            **kwargs: Incomplete,
        ) -> None: ...
        propSet: list[PropertyCollector.PropertySpec]
        objectSet: list[PropertyCollector.ObjectSpec]
        def __getattr__(self, name: str) -> Incomplete: ...

    class ObjectContent:
        def __init__(self, *, obj: ManagedEntity = ..., propSet: list[DynamicProperty] = ..., **kwargs: Incomplete) -> None: ...
        obj: ManagedEntity
        propSet: list[DynamicProperty]
        def __getattr__(self, name: str) -> Incomplete: ...

    class RetrieveResult:
        def __init__(self, *, objects: list[PropertyCollector.ObjectContent] = ..., token: str | None = ...) -> None: ...
        objects: list[PropertyCollector.ObjectContent]
        token: str | None
    def RetrievePropertiesEx(
        self, specSet: list[PropertyCollector.FilterSpec], options: PropertyCollector.RetrieveOptions
    ) -> PropertyCollector.RetrieveResult: ...
    def ContinueRetrievePropertiesEx(self, token: str) -> PropertyCollector.RetrieveResult: ...
    def __getattr__(self, name: str) -> Incomplete: ...
