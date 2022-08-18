from typing import Any

from ._chart import ChartBase as ChartBase

class ScatterChart(ChartBase):
    tagname: str
    scatterStyle: Any
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
        scatterStyle: Any | None = ...,
        varyColors: Any | None = ...,
        ser=...,
        dLbls: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
