from abc import abstractmethod
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class AxId(Serialisable):  # type: ignore[misc]
    val: Any
    def __init__(self, val) -> None: ...

def PlotArea(): ...

class ChartBase(Serialisable):
    legend: Any
    layout: Any
    roundedCorners: Any
    axId: Any
    visible_cells_only: Any
    display_blanks: Any
    ser: Any
    series: Any
    title: Any
    anchor: str
    width: int
    height: float
    style: Any
    mime_type: str
    graphical_properties: Any
    __elements__: Any
    plot_area: Any
    pivotSource: Any
    pivotFormats: Any
    idx_base: int
    def __init__(self, axId=..., **kw) -> None: ...
    def __hash__(self): ...
    def __iadd__(self, other): ...
    def to_tree(self, namespace: Any | None = ..., tagname: Any | None = ..., idx: Any | None = ...): ...  # type: ignore[override]
    def set_categories(self, labels) -> None: ...
    def add_data(self, data, from_rows: bool = ..., titles_from_data: bool = ...) -> None: ...
    def append(self, value) -> None: ...
    @property
    def path(self): ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...
