from typing import Any

from openpyxl.descriptors import Strict
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.styles.styleable import StyleableObject
from openpyxl.utils.bound_dictionary import BoundDictionary

class Dimension(Strict, StyleableObject):
    __fields__: Any
    index: Any
    hidden: Any
    outlineLevel: Any
    outline_level: Any
    collapsed: Any
    style: Any
    def __init__(
        self, index, hidden, outlineLevel, collapsed, worksheet, visible: bool = ..., style: Any | None = ...
    ) -> None: ...
    def __iter__(self): ...
    def __copy__(self): ...

class RowDimension(Dimension):
    __fields__: Any
    r: Any
    s: Any
    ht: Any
    height: Any
    thickBot: Any
    thickTop: Any
    def __init__(
        self,
        worksheet,
        index: int = ...,
        ht: Any | None = ...,
        customHeight: Any | None = ...,
        s: Any | None = ...,
        customFormat: Any | None = ...,
        hidden: bool = ...,
        outlineLevel: int = ...,
        outline_level: Any | None = ...,
        collapsed: bool = ...,
        visible: Any | None = ...,
        height: Any | None = ...,
        r: Any | None = ...,
        spans: Any | None = ...,
        thickBot: Any | None = ...,
        thickTop: Any | None = ...,
        **kw,
    ) -> None: ...
    @property
    def customFormat(self): ...
    @property
    def customHeight(self): ...

class ColumnDimension(Dimension):
    width: Any
    bestFit: Any
    auto_size: Any
    index: Any
    min: Any
    max: Any
    collapsed: Any
    __fields__: Any
    def __init__(
        self,
        worksheet,
        index: str = ...,
        width=...,
        bestFit: bool = ...,
        hidden: bool = ...,
        outlineLevel: int = ...,
        outline_level: Any | None = ...,
        collapsed: bool = ...,
        style: Any | None = ...,
        min: Any | None = ...,
        max: Any | None = ...,
        customWidth: bool = ...,
        visible: Any | None = ...,
        auto_size: Any | None = ...,
    ) -> None: ...
    @property
    def customWidth(self): ...
    def reindex(self) -> None: ...
    def to_tree(self): ...

class DimensionHolder(BoundDictionary):
    worksheet: Any
    max_outline: Any
    default_factory: Any
    def __init__(self, worksheet, reference: str = ..., default_factory: Any | None = ...) -> None: ...
    def group(self, start, end: Any | None = ..., outline_level: int = ..., hidden: bool = ...) -> None: ...
    def to_tree(self): ...

class SheetFormatProperties(Serialisable):
    tagname: str
    baseColWidth: Any
    defaultColWidth: Any
    defaultRowHeight: Any
    customHeight: Any
    zeroHeight: Any
    thickTop: Any
    thickBottom: Any
    outlineLevelRow: Any
    outlineLevelCol: Any
    def __init__(
        self,
        baseColWidth: int = ...,
        defaultColWidth: Any | None = ...,
        defaultRowHeight: int = ...,
        customHeight: Any | None = ...,
        zeroHeight: Any | None = ...,
        thickTop: Any | None = ...,
        thickBottom: Any | None = ...,
        outlineLevelRow: Any | None = ...,
        outlineLevelCol: Any | None = ...,
    ) -> None: ...

class SheetDimension(Serialisable):
    tagname: str
    ref: Any
    def __init__(self, ref: Any | None = ...) -> None: ...
    @property
    def boundaries(self): ...
