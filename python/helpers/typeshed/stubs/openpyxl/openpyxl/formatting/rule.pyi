from _typeshed import Incomplete, Unused
from typing import ClassVar, overload
from typing_extensions import Literal, TypeAlias

from openpyxl.descriptors import Float, Strict
from openpyxl.descriptors.base import Bool, Integer, NoneSet, Set, String, Typed, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.excel import ExtensionList
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.styles.colors import Color, ColorDescriptor
from openpyxl.styles.differential import DifferentialStyle

_IconSetIconSet: TypeAlias = Literal[
    "3Arrows",
    "3ArrowsGray",
    "3Flags",
    "3TrafficLights1",
    "3TrafficLights2",
    "3Signs",
    "3Symbols",
    "3Symbols2",
    "4Arrows",
    "4ArrowsGray",
    "4RedToBlack",
    "4Rating",
    "4TrafficLights",
    "5Arrows",
    "5ArrowsGray",
    "5Rating",
    "5Quarters",
]
_RuleOperator: TypeAlias = Literal[
    "lessThan",
    "lessThanOrEqual",
    "equal",
    "notEqual",
    "greaterThanOrEqual",
    "greaterThan",
    "between",
    "notBetween",
    "containsText",
    "notContains",
    "beginsWith",
    "endsWith",
]
_RuleTimePeriod: TypeAlias = Literal[
    "today", "yesterday", "tomorrow", "last7Days", "thisMonth", "lastMonth", "nextMonth", "thisWeek", "lastWeek", "nextWeek"
]
_FormatObjectType: TypeAlias = Literal["num", "percent", "max", "min", "formula", "percentile"]
_RuleType: TypeAlias = Literal[
    "expression",
    "cellIs",
    "colorScale",
    "dataBar",
    "iconSet",
    "top10",
    "uniqueValues",
    "duplicateValues",
    "containsText",
    "notContainsText",
    "beginsWith",
    "endsWith",
    "containsBlanks",
    "notContainsBlanks",
    "containsErrors",
    "notContainsErrors",
    "timePeriod",
    "aboveAverage",
]

class ValueDescriptor(Float[Incomplete]):
    expected_type: type[Incomplete]
    def __set__(self, instance: Serialisable | Strict, value) -> None: ...  # type: ignore[override]

class FormatObject(Serialisable):
    tagname: ClassVar[str]
    type: Set[_FormatObjectType]
    val: Incomplete
    gte: Bool[Literal[True]]
    extLst: Typed[ExtensionList, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self, type: _FormatObjectType, val: Incomplete | None = None, gte: _ConvertibleToBool | None = None, extLst: Unused = None
    ) -> None: ...

class RuleType(Serialisable):
    cfvo: Incomplete

class IconSet(RuleType):
    tagname: ClassVar[str]
    iconSet: NoneSet[_IconSetIconSet]
    showValue: Bool[Literal[True]]
    percent: Bool[Literal[True]]
    reverse: Bool[Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    cfvo: Incomplete
    def __init__(
        self,
        iconSet: _IconSetIconSet | Literal["none"] | None = None,
        showValue: _ConvertibleToBool | None = None,
        percent: _ConvertibleToBool | None = None,
        reverse: _ConvertibleToBool | None = None,
        cfvo: Incomplete | None = None,
    ) -> None: ...

class DataBar(RuleType):
    tagname: ClassVar[str]
    minLength: Integer[Literal[True]]
    maxLength: Integer[Literal[True]]
    showValue: Bool[Literal[True]]
    color: ColorDescriptor[Literal[False]]
    __elements__: ClassVar[tuple[str, ...]]
    cfvo: Incomplete
    @overload
    def __init__(
        self,
        minLength: _ConvertibleToInt | None = None,
        maxLength: _ConvertibleToInt | None = None,
        showValue: _ConvertibleToBool | None = None,
        cfvo: Incomplete | None = None,
        *,
        color: str | Color,
    ) -> None: ...
    @overload
    def __init__(
        self,
        minLength: _ConvertibleToInt | None,
        maxLength: _ConvertibleToInt | None,
        showValue: _ConvertibleToBool | None,
        cfvo: Incomplete | None,
        color: str | Color,
    ) -> None: ...

class ColorScale(RuleType):
    tagname: ClassVar[str]
    color: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    cfvo: Incomplete
    def __init__(self, cfvo: Incomplete | None = None, color: Incomplete | None = None) -> None: ...

class Rule(Serialisable):
    tagname: ClassVar[str]
    type: Set[_RuleType]
    dxfId: Integer[Literal[True]]
    priority: Integer[Literal[False]]
    stopIfTrue: Bool[Literal[True]]
    aboveAverage: Bool[Literal[True]]
    percent: Bool[Literal[True]]
    bottom: Bool[Literal[True]]
    operator: NoneSet[_RuleOperator]
    text: String[Literal[True]]
    timePeriod: NoneSet[_RuleTimePeriod]
    rank: Integer[Literal[True]]
    stdDev: Integer[Literal[True]]
    equalAverage: Bool[Literal[True]]
    formula: Incomplete
    colorScale: Typed[ColorScale, Literal[True]]
    dataBar: Typed[DataBar, Literal[True]]
    iconSet: Typed[IconSet, Literal[True]]
    extLst: Typed[ExtensionList, Literal[True]]
    dxf: Typed[DifferentialStyle, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    __attrs__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        type: _RuleType,
        dxfId: _ConvertibleToInt | None = None,
        priority: _ConvertibleToInt = 0,
        stopIfTrue: _ConvertibleToBool | None = None,
        aboveAverage: _ConvertibleToBool | None = None,
        percent: _ConvertibleToBool | None = None,
        bottom: _ConvertibleToBool | None = None,
        operator: _RuleOperator | Literal["none"] | None = None,
        text: str | None = None,
        timePeriod: _RuleTimePeriod | Literal["none"] | None = None,
        rank: _ConvertibleToInt | None = None,
        stdDev: _ConvertibleToInt | None = None,
        equalAverage: _ConvertibleToBool | None = None,
        formula=(),
        colorScale: ColorScale | None = None,
        dataBar: DataBar | None = None,
        iconSet: IconSet | None = None,
        extLst: Unused = None,
        dxf: DifferentialStyle | None = None,
    ) -> None: ...

def ColorScaleRule(
    start_type: Incomplete | None = None,
    start_value: Incomplete | None = None,
    start_color: Incomplete | None = None,
    mid_type: Incomplete | None = None,
    mid_value: Incomplete | None = None,
    mid_color: Incomplete | None = None,
    end_type: Incomplete | None = None,
    end_value: Incomplete | None = None,
    end_color: Incomplete | None = None,
): ...
def FormulaRule(
    formula: Incomplete | None = None,
    stopIfTrue: Incomplete | None = None,
    font: Incomplete | None = None,
    border: Incomplete | None = None,
    fill: Incomplete | None = None,
): ...
def CellIsRule(
    operator: Incomplete | None = None,
    formula: Incomplete | None = None,
    stopIfTrue: Incomplete | None = None,
    font: Incomplete | None = None,
    border: Incomplete | None = None,
    fill: Incomplete | None = None,
): ...
def IconSetRule(
    icon_style: Incomplete | None = None,
    type: Incomplete | None = None,
    values: Incomplete | None = None,
    showValue: Incomplete | None = None,
    percent: Incomplete | None = None,
    reverse: Incomplete | None = None,
): ...
def DataBarRule(
    start_type: Incomplete | None = None,
    start_value: Incomplete | None = None,
    end_type: Incomplete | None = None,
    end_value: Incomplete | None = None,
    color: Incomplete | None = None,
    showValue: Incomplete | None = None,
    minLength: Incomplete | None = None,
    maxLength: Incomplete | None = None,
): ...
