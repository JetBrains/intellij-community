from typing import Any

from ._chart import ChartBase

class StockChart(ChartBase):
    tagname: str
    ser: Any
    dLbls: Any
    dataLabels: Any
    dropLines: Any
    hiLowLines: Any
    upDownBars: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    __elements__: Any
    def __init__(
        self,
        ser=...,
        dLbls: Any | None = ...,
        dropLines: Any | None = ...,
        hiLowLines: Any | None = ...,
        upDownBars: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
