from _typeshed import Unused
from typing import ClassVar
from typing_extensions import Literal, TypeAlias

from openpyxl.chart.data_source import NumDataSource
from openpyxl.chart.shapes import GraphicalProperties
from openpyxl.descriptors.base import Alias, Typed, _ConvertibleToBool, _ConvertibleToFloat
from openpyxl.descriptors.excel import ExtensionList
from openpyxl.descriptors.nested import NestedBool, NestedFloat, NestedNoneSet, NestedSet, _NestedNoneSetParam
from openpyxl.descriptors.serialisable import Serialisable

from ..xml._functions_overloads import _HasTagAndGet

_ErrorBarsErrBarType: TypeAlias = Literal["both", "minus", "plus"]
_ErrorBarsErrValType: TypeAlias = Literal["cust", "fixedVal", "percentage", "stdDev", "stdErr"]
_ErrorBarsErrDir: TypeAlias = Literal["x", "y"]

class ErrorBars(Serialisable):
    tagname: ClassVar[str]
    errDir: NestedNoneSet[_ErrorBarsErrDir]
    direction: Alias
    errBarType: NestedSet[_ErrorBarsErrBarType]
    style: Alias
    errValType: NestedSet[_ErrorBarsErrValType]
    size: Alias
    noEndCap: NestedBool[Literal[True]]
    plus: Typed[NumDataSource, Literal[True]]
    minus: Typed[NumDataSource, Literal[True]]
    val: NestedFloat[Literal[True]]
    spPr: Typed[GraphicalProperties, Literal[True]]
    graphicalProperties: Alias
    extLst: Typed[ExtensionList, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        errDir: _NestedNoneSetParam[_ErrorBarsErrDir] = None,
        errBarType: _HasTagAndGet[_ErrorBarsErrBarType] | _ErrorBarsErrBarType = "both",
        errValType: _HasTagAndGet[_ErrorBarsErrValType] | _ErrorBarsErrValType = "fixedVal",
        noEndCap: _HasTagAndGet[_ConvertibleToBool | None] | _ConvertibleToBool | None = None,
        plus: NumDataSource | None = None,
        minus: NumDataSource | None = None,
        val: _HasTagAndGet[_ConvertibleToFloat | None] | _ConvertibleToFloat | None = None,
        spPr: GraphicalProperties | None = None,
        extLst: Unused = None,
    ) -> None: ...
