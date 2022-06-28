from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ChartContainer(Serialisable):
    tagname: str
    title: Any
    autoTitleDeleted: Any
    pivotFmts: Any
    view3D: Any
    floor: Any
    sideWall: Any
    backWall: Any
    plotArea: Any
    legend: Any
    plotVisOnly: Any
    dispBlanksAs: Any
    showDLblsOverMax: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        title: Any | None = ...,
        autoTitleDeleted: Any | None = ...,
        pivotFmts=...,
        view3D: Any | None = ...,
        floor: Any | None = ...,
        sideWall: Any | None = ...,
        backWall: Any | None = ...,
        plotArea: Any | None = ...,
        legend: Any | None = ...,
        plotVisOnly: bool = ...,
        dispBlanksAs: str = ...,
        showDLblsOverMax: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Protection(Serialisable):
    tagname: str
    chartObject: Any
    data: Any
    formatting: Any
    selection: Any
    userInterface: Any
    __elements__: Any
    def __init__(
        self,
        chartObject: Any | None = ...,
        data: Any | None = ...,
        formatting: Any | None = ...,
        selection: Any | None = ...,
        userInterface: Any | None = ...,
    ) -> None: ...

class ExternalData(Serialisable):
    tagname: str
    autoUpdate: Any
    id: Any
    def __init__(self, autoUpdate: Any | None = ..., id: Any | None = ...) -> None: ...

class ChartSpace(Serialisable):
    tagname: str
    date1904: Any
    lang: Any
    roundedCorners: Any
    style: Any
    clrMapOvr: Any
    pivotSource: Any
    protection: Any
    chart: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    textProperties: Any
    externalData: Any
    printSettings: Any
    userShapes: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        date1904: Any | None = ...,
        lang: Any | None = ...,
        roundedCorners: Any | None = ...,
        style: Any | None = ...,
        clrMapOvr: Any | None = ...,
        pivotSource: Any | None = ...,
        protection: Any | None = ...,
        chart: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        externalData: Any | None = ...,
        printSettings: Any | None = ...,
        userShapes: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ..., namespace: Any | None = ...): ...
