from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

from .cell_range import CellRange

class MergeCell(CellRange):
    tagname: str
    ref: Any
    __attrs__: Any
    def __init__(self, ref: Any | None = ...) -> None: ...
    def __copy__(self): ...

class MergeCells(Serialisable):
    tagname: str
    # Overwritten by property below
    # count: Integer
    mergeCell: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., mergeCell=...) -> None: ...
    @property
    def count(self): ...

class MergedCellRange(CellRange):
    ws: Any
    start_cell: Any
    def __init__(self, worksheet, coord) -> None: ...
    def format(self) -> None: ...
    def __contains__(self, coord): ...
    def __copy__(self): ...
