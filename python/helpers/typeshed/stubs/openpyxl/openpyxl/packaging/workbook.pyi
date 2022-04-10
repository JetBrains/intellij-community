from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class FileRecoveryProperties(Serialisable):
    tagname: str
    autoRecover: Any
    crashSave: Any
    dataExtractLoad: Any
    repairLoad: Any
    def __init__(
        self,
        autoRecover: Any | None = ...,
        crashSave: Any | None = ...,
        dataExtractLoad: Any | None = ...,
        repairLoad: Any | None = ...,
    ) -> None: ...

class ChildSheet(Serialisable):
    tagname: str
    name: Any
    sheetId: Any
    state: Any
    id: Any
    def __init__(self, name: Any | None = ..., sheetId: Any | None = ..., state: str = ..., id: Any | None = ...) -> None: ...

class PivotCache(Serialisable):
    tagname: str
    cacheId: Any
    id: Any
    def __init__(self, cacheId: Any | None = ..., id: Any | None = ...) -> None: ...

class WorkbookPackage(Serialisable):
    tagname: str
    conformance: Any
    fileVersion: Any
    fileSharing: Any
    workbookPr: Any
    properties: Any
    workbookProtection: Any
    bookViews: Any
    sheets: Any
    functionGroups: Any
    externalReferences: Any
    definedNames: Any
    calcPr: Any
    oleSize: Any
    customWorkbookViews: Any
    pivotCaches: Any
    smartTagPr: Any
    smartTagTypes: Any
    webPublishing: Any
    fileRecoveryPr: Any
    webPublishObjects: Any
    extLst: Any
    Ignorable: Any
    __elements__: Any
    def __init__(
        self,
        conformance: Any | None = ...,
        fileVersion: Any | None = ...,
        fileSharing: Any | None = ...,
        workbookPr: Any | None = ...,
        workbookProtection: Any | None = ...,
        bookViews=...,
        sheets=...,
        functionGroups: Any | None = ...,
        externalReferences=...,
        definedNames: Any | None = ...,
        calcPr: Any | None = ...,
        oleSize: Any | None = ...,
        customWorkbookViews=...,
        pivotCaches=...,
        smartTagPr: Any | None = ...,
        smartTagTypes: Any | None = ...,
        webPublishing: Any | None = ...,
        fileRecoveryPr: Any | None = ...,
        webPublishObjects: Any | None = ...,
        extLst: Any | None = ...,
        Ignorable: Any | None = ...,
    ) -> None: ...
    def to_tree(self): ...
    @property
    def active(self): ...
    @property
    def pivot_caches(self): ...
