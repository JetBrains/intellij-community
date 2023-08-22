from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class NamedStyle(Serialisable):  # type: ignore[misc]
    font: Any
    fill: Any
    border: Any
    alignment: Any
    number_format: Any
    protection: Any
    builtinId: Any
    hidden: Any
    # Overwritten by property below
    # xfId: Integer
    name: Any
    def __init__(
        self,
        name: str = ...,
        font=...,
        fill=...,
        border=...,
        alignment=...,
        number_format: Any | None = ...,
        protection=...,
        builtinId: Any | None = ...,
        hidden: bool = ...,
        xfId: Any | None = ...,
    ) -> None: ...
    def __setattr__(self, attr, value) -> None: ...
    def __iter__(self): ...
    @property
    def xfId(self): ...
    def bind(self, wb) -> None: ...
    def as_tuple(self): ...
    def as_xf(self): ...
    def as_name(self): ...

class NamedStyleList(list[Any]):
    @property
    def names(self): ...
    def __getitem__(self, key): ...
    def append(self, style) -> None: ...

class _NamedCellStyle(Serialisable):
    tagname: str
    name: Any
    xfId: Any
    builtinId: Any
    iLevel: Any
    hidden: Any
    customBuiltin: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        xfId: Any | None = ...,
        builtinId: Any | None = ...,
        iLevel: Any | None = ...,
        hidden: Any | None = ...,
        customBuiltin: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class _NamedCellStyleList(Serialisable):
    tagname: str
    # Overwritten by property below
    # count: Integer
    cellStyle: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., cellStyle=...) -> None: ...
    @property
    def count(self): ...
    @property
    def names(self): ...
