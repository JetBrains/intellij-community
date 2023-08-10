from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Stylesheet(Serialisable):
    tagname: str
    numFmts: Any
    fonts: Any
    fills: Any
    borders: Any
    cellStyleXfs: Any
    cellXfs: Any
    cellStyles: Any
    dxfs: Any
    tableStyles: Any
    colors: Any
    extLst: Any
    __elements__: Any
    number_formats: Any
    cell_styles: Any
    alignments: Any
    protections: Any
    named_styles: Any
    def __init__(
        self,
        numFmts: Any | None = ...,
        fonts=...,
        fills=...,
        borders=...,
        cellStyleXfs: Any | None = ...,
        cellXfs: Any | None = ...,
        cellStyles: Any | None = ...,
        dxfs=...,
        tableStyles: Any | None = ...,
        colors: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    @classmethod
    def from_tree(cls, node): ...
    @property
    def custom_formats(self): ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ..., namespace: Any | None = ...): ...

def apply_stylesheet(archive, wb): ...
def write_stylesheet(wb): ...
