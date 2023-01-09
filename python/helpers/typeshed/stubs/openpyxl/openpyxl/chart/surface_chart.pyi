from abc import abstractmethod
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

from ._3d import _3DBase
from ._chart import ChartBase

class BandFormat(Serialisable):
    tagname: str
    idx: Any
    spPr: Any
    graphicalProperties: Any
    __elements__: Any
    def __init__(self, idx: int = ..., spPr: Any | None = ...) -> None: ...

class BandFormatList(Serialisable):
    tagname: str
    bandFmt: Any
    __elements__: Any
    def __init__(self, bandFmt=...) -> None: ...

class _SurfaceChartBase(ChartBase):
    wireframe: Any
    ser: Any
    bandFmts: Any
    __elements__: Any
    def __init__(self, wireframe: Any | None = ..., ser=..., bandFmts: Any | None = ..., **kw) -> None: ...
    @property
    @abstractmethod
    def tagname(self) -> str: ...

class SurfaceChart3D(_SurfaceChartBase, _3DBase):
    tagname: str
    wireframe: Any
    ser: Any
    bandFmts: Any
    extLst: Any
    x_axis: Any
    y_axis: Any
    z_axis: Any
    __elements__: Any
    def __init__(self, **kw) -> None: ...

class SurfaceChart(SurfaceChart3D):
    tagname: str
    wireframe: Any
    ser: Any
    bandFmts: Any
    extLst: Any
    __elements__: Any
    def __init__(self, **kw) -> None: ...
