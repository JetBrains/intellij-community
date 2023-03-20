from typing import Any

from ._chart import ChartBase

class BubbleChart(ChartBase):
    tagname: str
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    bubble3D: Any
    bubbleScale: Any
    showNegBubbles: Any
    sizeRepresents: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    __elements__: Any
    def __init__(
        self,
        varyColors: Any | None = ...,
        ser=...,
        dLbls: Any | None = ...,
        bubble3D: Any | None = ...,
        bubbleScale: Any | None = ...,
        showNegBubbles: Any | None = ...,
        sizeRepresents: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
