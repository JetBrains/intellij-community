from abc import abstractmethod
from typing import Any

from ._chart import ChartBase

class _AreaChartBase(ChartBase):
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    dropLines: Any
    __elements__: Any
    def __init__(
        self, grouping: str = ..., varyColors: Any | None = ..., ser=..., dLbls: Any | None = ..., dropLines: Any | None = ...
    ) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class AreaChart(_AreaChartBase):
    tagname: str
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dropLines: Any
    x_axis: Any
    y_axis: Any
    extLst: Any
    __elements__: Any
    def __init__(self, axId: Any | None = ..., extLst: Any | None = ..., **kw) -> None: ...

class AreaChart3D(AreaChart):
    tagname: str
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dropLines: Any
    gapDepth: Any
    x_axis: Any
    y_axis: Any
    z_axis: Any
    __elements__: Any
    def __init__(self, gapDepth: Any | None = ..., **kw) -> None: ...
