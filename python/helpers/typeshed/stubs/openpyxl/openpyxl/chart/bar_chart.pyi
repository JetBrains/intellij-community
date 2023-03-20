from abc import abstractmethod
from typing import Any

from ._3d import _3DBase
from ._chart import ChartBase

class _BarChartBase(ChartBase):
    barDir: Any
    type: Any
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    __elements__: Any
    def __init__(
        self, barDir: str = ..., grouping: str = ..., varyColors: Any | None = ..., ser=..., dLbls: Any | None = ..., **kw
    ) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class BarChart(_BarChartBase):
    tagname: str
    barDir: Any
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    gapWidth: Any
    overlap: Any
    serLines: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    __elements__: Any
    legend: Any
    def __init__(
        self, gapWidth: int = ..., overlap: Any | None = ..., serLines: Any | None = ..., extLst: Any | None = ..., **kw
    ) -> None: ...

class BarChart3D(_BarChartBase, _3DBase):
    tagname: str
    barDir: Any
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    view3D: Any
    floor: Any
    sideWall: Any
    backWall: Any
    gapWidth: Any
    gapDepth: Any
    shape: Any
    serLines: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    z_axis: Any
    __elements__: Any
    def __init__(
        self,
        gapWidth: int = ...,
        gapDepth: int = ...,
        shape: Any | None = ...,
        serLines: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
