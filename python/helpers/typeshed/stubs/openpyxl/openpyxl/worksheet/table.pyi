from typing import Any

from openpyxl.descriptors import String
from openpyxl.descriptors.serialisable import Serialisable

TABLESTYLES: Any
PIVOTSTYLES: Any

class TableStyleInfo(Serialisable):
    tagname: str
    name: Any
    showFirstColumn: Any
    showLastColumn: Any
    showRowStripes: Any
    showColumnStripes: Any
    def __init__(
        self,
        name: Any | None = ...,
        showFirstColumn: Any | None = ...,
        showLastColumn: Any | None = ...,
        showRowStripes: Any | None = ...,
        showColumnStripes: Any | None = ...,
    ) -> None: ...

class XMLColumnProps(Serialisable):
    tagname: str
    mapId: Any
    xpath: Any
    denormalized: Any
    xmlDataType: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        mapId: Any | None = ...,
        xpath: Any | None = ...,
        denormalized: Any | None = ...,
        xmlDataType: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class TableFormula(Serialisable):
    tagname: str
    array: Any
    attr_text: Any
    text: Any
    def __init__(self, array: Any | None = ..., attr_text: Any | None = ...) -> None: ...

class TableColumn(Serialisable):
    tagname: str
    id: Any
    uniqueName: Any
    name: Any
    totalsRowFunction: Any
    totalsRowLabel: Any
    queryTableFieldId: Any
    headerRowDxfId: Any
    dataDxfId: Any
    totalsRowDxfId: Any
    headerRowCellStyle: Any
    dataCellStyle: Any
    totalsRowCellStyle: Any
    calculatedColumnFormula: Any
    totalsRowFormula: Any
    xmlColumnPr: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        id: Any | None = ...,
        uniqueName: Any | None = ...,
        name: Any | None = ...,
        totalsRowFunction: Any | None = ...,
        totalsRowLabel: Any | None = ...,
        queryTableFieldId: Any | None = ...,
        headerRowDxfId: Any | None = ...,
        dataDxfId: Any | None = ...,
        totalsRowDxfId: Any | None = ...,
        headerRowCellStyle: Any | None = ...,
        dataCellStyle: Any | None = ...,
        totalsRowCellStyle: Any | None = ...,
        calculatedColumnFormula: Any | None = ...,
        totalsRowFormula: Any | None = ...,
        xmlColumnPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    def __iter__(self): ...
    @classmethod
    def from_tree(cls, node): ...

class TableNameDescriptor(String):
    def __set__(self, instance, value) -> None: ...

class Table(Serialisable):
    mime_type: str
    tagname: str
    id: Any
    name: Any
    displayName: Any
    comment: Any
    ref: Any
    tableType: Any
    headerRowCount: Any
    insertRow: Any
    insertRowShift: Any
    totalsRowCount: Any
    totalsRowShown: Any
    published: Any
    headerRowDxfId: Any
    dataDxfId: Any
    totalsRowDxfId: Any
    headerRowBorderDxfId: Any
    tableBorderDxfId: Any
    totalsRowBorderDxfId: Any
    headerRowCellStyle: Any
    dataCellStyle: Any
    totalsRowCellStyle: Any
    connectionId: Any
    autoFilter: Any
    sortState: Any
    tableColumns: Any
    tableStyleInfo: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        id: int = ...,
        displayName: Any | None = ...,
        ref: Any | None = ...,
        name: Any | None = ...,
        comment: Any | None = ...,
        tableType: Any | None = ...,
        headerRowCount: int = ...,
        insertRow: Any | None = ...,
        insertRowShift: Any | None = ...,
        totalsRowCount: Any | None = ...,
        totalsRowShown: Any | None = ...,
        published: Any | None = ...,
        headerRowDxfId: Any | None = ...,
        dataDxfId: Any | None = ...,
        totalsRowDxfId: Any | None = ...,
        headerRowBorderDxfId: Any | None = ...,
        tableBorderDxfId: Any | None = ...,
        totalsRowBorderDxfId: Any | None = ...,
        headerRowCellStyle: Any | None = ...,
        dataCellStyle: Any | None = ...,
        totalsRowCellStyle: Any | None = ...,
        connectionId: Any | None = ...,
        autoFilter: Any | None = ...,
        sortState: Any | None = ...,
        tableColumns=...,
        tableStyleInfo: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    def to_tree(self): ...
    @property
    def path(self): ...
    @property
    def column_names(self): ...

class TablePartList(Serialisable):
    tagname: str
    # Overwritten by property below
    # count: Integer
    tablePart: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., tablePart=...) -> None: ...
    def append(self, part) -> None: ...
    @property
    def count(self): ...
    def __bool__(self): ...

class TableList(dict[Any, Any]):
    def add(self, table) -> None: ...
    def get(self, name: Any | None = ..., table_range: Any | None = ...): ...
    def items(self): ...
