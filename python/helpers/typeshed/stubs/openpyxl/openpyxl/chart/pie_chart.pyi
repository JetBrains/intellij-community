from abc import abstractmethod
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

from ._chart import ChartBase

class _PieChartBase(ChartBase):
    varyColors: Any
    ser: Any
    dLbls: Any
    dataLabels: Any
    __elements__: Any
    def __init__(self, varyColors: bool = ..., ser=..., dLbls: Any | None = ...) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class PieChart(_PieChartBase):
    tagname: str
    varyColors: Any
    ser: Any
    dLbls: Any
    firstSliceAng: Any
    extLst: Any
    __elements__: Any
    def __init__(self, firstSliceAng: int = ..., extLst: Any | None = ..., **kw) -> None: ...

class PieChart3D(_PieChartBase):
    tagname: str
    varyColors: Any
    ser: Any
    dLbls: Any
    extLst: Any
    __elements__: Any

class DoughnutChart(_PieChartBase):
    tagname: str
    varyColors: Any
    ser: Any
    dLbls: Any
    firstSliceAng: Any
    holeSize: Any
    extLst: Any
    __elements__: Any
    def __init__(self, firstSliceAng: int = ..., holeSize: int = ..., extLst: Any | None = ..., **kw) -> None: ...

class CustomSplit(Serialisable):
    tagname: str
    secondPiePt: Any
    __elements__: Any
    def __init__(self, secondPiePt=...) -> None: ...

class ProjectedPieChart(_PieChartBase):
    tagname: str
    varyColors: Any
    ser: Any
    dLbls: Any
    ofPieType: Any
    type: Any
    gapWidth: Any
    splitType: Any
    splitPos: Any
    custSplit: Any
    secondPieSize: Any
    serLines: Any
    join_lines: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        ofPieType: str = ...,
        gapWidth: Any | None = ...,
        splitType: str = ...,
        splitPos: Any | None = ...,
        custSplit: Any | None = ...,
        secondPieSize: int = ...,
        serLines: Any | None = ...,
        extLst: Any | None = ...,
        **kw,
    ) -> None: ...
