from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class SortCondition(Serialisable):
    tagname: str
    descending: Any
    sortBy: Any
    ref: Any
    customList: Any
    dxfId: Any
    iconSet: Any
    iconId: Any
    def __init__(
        self,
        ref: Any | None = ...,
        descending: Any | None = ...,
        sortBy: Any | None = ...,
        customList: Any | None = ...,
        dxfId: Any | None = ...,
        iconSet: Any | None = ...,
        iconId: Any | None = ...,
    ) -> None: ...

class SortState(Serialisable):
    tagname: str
    columnSort: Any
    caseSensitive: Any
    sortMethod: Any
    ref: Any
    sortCondition: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        columnSort: Any | None = ...,
        caseSensitive: Any | None = ...,
        sortMethod: Any | None = ...,
        ref: Any | None = ...,
        sortCondition=...,
        extLst: Any | None = ...,
    ) -> None: ...
    def __bool__(self): ...

class IconFilter(Serialisable):
    tagname: str
    iconSet: Any
    iconId: Any
    def __init__(self, iconSet: Any | None = ..., iconId: Any | None = ...) -> None: ...

class ColorFilter(Serialisable):
    tagname: str
    dxfId: Any
    cellColor: Any
    def __init__(self, dxfId: Any | None = ..., cellColor: Any | None = ...) -> None: ...

class DynamicFilter(Serialisable):
    tagname: str
    type: Any
    val: Any
    valIso: Any
    maxVal: Any
    maxValIso: Any
    def __init__(
        self,
        type: Any | None = ...,
        val: Any | None = ...,
        valIso: Any | None = ...,
        maxVal: Any | None = ...,
        maxValIso: Any | None = ...,
    ) -> None: ...

class CustomFilter(Serialisable):
    tagname: str
    operator: Any
    val: Any
    def __init__(self, operator: Any | None = ..., val: Any | None = ...) -> None: ...

class CustomFilters(Serialisable):
    tagname: str
    customFilter: Any
    __elements__: Any
    def __init__(self, _and: Any | None = ..., customFilter=...) -> None: ...

class Top10(Serialisable):
    tagname: str
    top: Any
    percent: Any
    val: Any
    filterVal: Any
    def __init__(
        self, top: Any | None = ..., percent: Any | None = ..., val: Any | None = ..., filterVal: Any | None = ...
    ) -> None: ...

class DateGroupItem(Serialisable):
    tagname: str
    year: Any
    month: Any
    day: Any
    hour: Any
    minute: Any
    second: Any
    dateTimeGrouping: Any
    def __init__(
        self,
        year: Any | None = ...,
        month: Any | None = ...,
        day: Any | None = ...,
        hour: Any | None = ...,
        minute: Any | None = ...,
        second: Any | None = ...,
        dateTimeGrouping: Any | None = ...,
    ) -> None: ...

class Filters(Serialisable):
    tagname: str
    blank: Any
    calendarType: Any
    filter: Any
    dateGroupItem: Any
    __elements__: Any
    def __init__(self, blank: Any | None = ..., calendarType: Any | None = ..., filter=..., dateGroupItem=...) -> None: ...

class FilterColumn(Serialisable):
    tagname: str
    colId: Any
    col_id: Any
    hiddenButton: Any
    showButton: Any
    filters: Any
    top10: Any
    customFilters: Any
    dynamicFilter: Any
    colorFilter: Any
    iconFilter: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        colId: Any | None = ...,
        hiddenButton: Any | None = ...,
        showButton: Any | None = ...,
        filters: Any | None = ...,
        top10: Any | None = ...,
        customFilters: Any | None = ...,
        dynamicFilter: Any | None = ...,
        colorFilter: Any | None = ...,
        iconFilter: Any | None = ...,
        extLst: Any | None = ...,
        blank: Any | None = ...,
        vals: Any | None = ...,
    ) -> None: ...

class AutoFilter(Serialisable):
    tagname: str
    ref: Any
    filterColumn: Any
    sortState: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, ref: Any | None = ..., filterColumn=..., sortState: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...
    def __bool__(self): ...
    def add_filter_column(self, col_id, vals, blank: bool = ...) -> None: ...
    def add_sort_condition(self, ref, descending: bool = ...) -> None: ...
