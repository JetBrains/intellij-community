from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class DataTable(Serialisable):
    tagname: str
    showHorzBorder: Any
    showVertBorder: Any
    showOutline: Any
    showKeys: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        showHorzBorder: Any | None = ...,
        showVertBorder: Any | None = ...,
        showOutline: Any | None = ...,
        showKeys: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class PlotArea(Serialisable):
    tagname: str
    layout: Any
    dTable: Any
    spPr: Any
    graphicalProperties: Any
    extLst: Any
    areaChart: Any
    area3DChart: Any
    lineChart: Any
    line3DChart: Any
    stockChart: Any
    radarChart: Any
    scatterChart: Any
    pieChart: Any
    pie3DChart: Any
    doughnutChart: Any
    barChart: Any
    bar3DChart: Any
    ofPieChart: Any
    surfaceChart: Any
    surface3DChart: Any
    bubbleChart: Any
    valAx: Any
    catAx: Any
    dateAx: Any
    serAx: Any
    __elements__: Any
    def __init__(
        self,
        layout: Any | None = ...,
        dTable: Any | None = ...,
        spPr: Any | None = ...,
        _charts=...,
        _axes=...,
        extLst: Any | None = ...,
    ) -> None: ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ..., namespace: Any | None = ...): ...
    @classmethod
    def from_tree(cls, node): ...
