from abc import abstractmethod
from typing import Any

from ._chart import ChartBase

class _LineChartBase(ChartBase):
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    dropLines: Any
    __elements__: Any
    def __init__(
        self,
        grouping: str = ...,
        varyColors: Any | None = ...,
        ser=...,
        dLbls: Any | None = ...,
        dropLines: Any | None = ...,
        **kw,
    ) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class LineChart(_LineChartBase):
    tagname: str
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dropLines: Any
    hiLowLines: Any
    upDownBars: Any
    marker: Any
    smooth: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    __elements__: Any
    def __init__(
        self,
        hiLowLines: Any | None = ...,
        upDownBars: Any | None = ...,
        marker: Any | None = ...,
        smooth: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...

class LineChart3D(_LineChartBase):
    tagname: str
    grouping: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dropLines: Any
    gapDepth: Any
    hiLowLines: Any
    upDownBars: Any
    marker: Any
    smooth: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    z_axis: Any
    __elements__: Any
    def __init__(
        self,
        gapDepth: Any | None = ...,
        hiLowLines: Any | None = ...,
        upDownBars: Any | None = ...,
        marker: Any | None = ...,
        smooth: Any | None = ...,
        **kw,
    ) -> None: ...
