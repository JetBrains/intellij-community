from typing import Any

from ._chart import ChartBase

class RadarChart(ChartBase):
    tagname: str
    radarStyle: Any
    type: Any
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    __elements__: Any
    def __init__(
        self,
        radarStyle: str = ...,
        varyColors: Any | None = ...,
        ser=...,
        dLbls: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
