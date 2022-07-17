from typing import Any

from openpyxl.descriptors import Float
from openpyxl.descriptors.serialisable import Serialisable

class ValueDescriptor(Float):
    expected_type: Any
    def __set__(self, instance, value) -> None: ...

class FormatObject(Serialisable):
    tagname: str
    type: Any
    val: Any
    gte: Any
    extLst: Any
    __elements__: Any
    def __init__(self, type, val: Any | None = ..., gte: Any | None = ..., extLst: Any | None = ...) -> None: ...

class RuleType(Serialisable):  # type: ignore[misc]
    cfvo: Any

class IconSet(RuleType):
    tagname: str
    iconSet: Any
    showValue: Any
    percent: Any
    reverse: Any
    __elements__: Any
    cfvo: Any
    def __init__(
        self,
        iconSet: Any | None = ...,
        showValue: Any | None = ...,
        percent: Any | None = ...,
        reverse: Any | None = ...,
        cfvo: Any | None = ...,
    ) -> None: ...

class DataBar(RuleType):
    tagname: str
    minLength: Any
    maxLength: Any
    showValue: Any
    color: Any
    __elements__: Any
    cfvo: Any
    def __init__(
        self,
        minLength: Any | None = ...,
        maxLength: Any | None = ...,
        showValue: Any | None = ...,
        cfvo: Any | None = ...,
        color: Any | None = ...,
    ) -> None: ...

class ColorScale(RuleType):
    tagname: str
    color: Any
    __elements__: Any
    cfvo: Any
    def __init__(self, cfvo: Any | None = ..., color: Any | None = ...) -> None: ...

class Rule(Serialisable):
    tagname: str
    type: Any
    dxfId: Any
    priority: Any
    stopIfTrue: Any
    aboveAverage: Any
    percent: Any
    bottom: Any
    operator: Any
    text: Any
    timePeriod: Any
    rank: Any
    stdDev: Any
    equalAverage: Any
    formula: Any
    colorScale: Any
    dataBar: Any
    iconSet: Any
    extLst: Any
    dxf: Any
    __elements__: Any
    __attrs__: Any
    def __init__(
        self,
        type,
        dxfId: Any | None = ...,
        priority: int = ...,
        stopIfTrue: Any | None = ...,
        aboveAverage: Any | None = ...,
        percent: Any | None = ...,
        bottom: Any | None = ...,
        operator: Any | None = ...,
        text: Any | None = ...,
        timePeriod: Any | None = ...,
        rank: Any | None = ...,
        stdDev: Any | None = ...,
        equalAverage: Any | None = ...,
        formula=...,
        colorScale: Any | None = ...,
        dataBar: Any | None = ...,
        iconSet: Any | None = ...,
        extLst: Any | None = ...,
        dxf: Any | None = ...,
    ) -> None: ...

def ColorScaleRule(
    start_type: Any | None = ...,
    start_value: Any | None = ...,
    start_color: Any | None = ...,
    mid_type: Any | None = ...,
    mid_value: Any | None = ...,
    mid_color: Any | None = ...,
    end_type: Any | None = ...,
    end_value: Any | None = ...,
    end_color: Any | None = ...,
): ...
def FormulaRule(
    formula: Any | None = ...,
    stopIfTrue: Any | None = ...,
    font: Any | None = ...,
    border: Any | None = ...,
    fill: Any | None = ...,
): ...
def CellIsRule(
    operator: Any | None = ...,
    formula: Any | None = ...,
    stopIfTrue: Any | None = ...,
    font: Any | None = ...,
    border: Any | None = ...,
    fill: Any | None = ...,
): ...
def IconSetRule(
    icon_style: Any | None = ...,
    type: Any | None = ...,
    values: Any | None = ...,
    showValue: Any | None = ...,
    percent: Any | None = ...,
    reverse: Any | None = ...,
): ...
def DataBarRule(
    start_type: Any | None = ...,
    start_value: Any | None = ...,
    end_type: Any | None = ...,
    end_value: Any | None = ...,
    color: Any | None = ...,
    showValue: Any | None = ...,
    minLength: Any | None = ...,
    maxLength: Any | None = ...,
): ...
