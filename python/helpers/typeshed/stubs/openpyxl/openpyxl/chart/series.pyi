from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

attribute_mapping: Any

class SeriesLabel(Serialisable):
    tagname: str
    strRef: Any
    v: Any
    value: Any
    __elements__: Any
    def __init__(self, strRef: Any | None = ..., v: Any | None = ...) -> None: ...

class Series(Serialisable):
    tagname: str
    idx: Any
    order: Any
    tx: Any
    title: Any
    spPr: Any
    graphicalProperties: Any
    pictureOptions: Any
    dPt: Any
    data_points: Any
    dLbls: Any
    labels: Any
    trendline: Any
    errBars: Any
    cat: Any
    identifiers: Any
    val: Any
    extLst: Any
    invertIfNegative: Any
    shape: Any
    xVal: Any
    yVal: Any
    bubbleSize: Any
    zVal: Any
    bubble3D: Any
    marker: Any
    smooth: Any
    explosion: Any
    __elements__: Any
    def __init__(
        self,
        idx: int = ...,
        order: int = ...,
        tx: Any | None = ...,
        spPr: Any | None = ...,
        pictureOptions: Any | None = ...,
        dPt=...,
        dLbls: Any | None = ...,
        trendline: Any | None = ...,
        errBars: Any | None = ...,
        cat: Any | None = ...,
        val: Any | None = ...,
        invertIfNegative: Any | None = ...,
        shape: Any | None = ...,
        xVal: Any | None = ...,
        yVal: Any | None = ...,
        bubbleSize: Any | None = ...,
        bubble3D: Any | None = ...,
        marker: Any | None = ...,
        smooth: Any | None = ...,
        explosion: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    def to_tree(self, tagname: Any | None = ..., idx: Any | None = ...): ...  # type: ignore[override]

class XYSeries(Series):
    idx: Any
    order: Any
    tx: Any
    spPr: Any
    dPt: Any
    dLbls: Any
    trendline: Any
    errBars: Any
    xVal: Any
    yVal: Any
    invertIfNegative: Any
    bubbleSize: Any
    bubble3D: Any
    marker: Any
    smooth: Any
