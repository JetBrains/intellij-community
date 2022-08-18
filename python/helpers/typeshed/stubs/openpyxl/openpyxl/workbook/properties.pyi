from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class WorkbookProperties(Serialisable):
    tagname: str
    date1904: Any
    dateCompatibility: Any
    showObjects: Any
    showBorderUnselectedTables: Any
    filterPrivacy: Any
    promptedSolutions: Any
    showInkAnnotation: Any
    backupFile: Any
    saveExternalLinkValues: Any
    updateLinks: Any
    codeName: Any
    hidePivotFieldList: Any
    showPivotChartFilter: Any
    allowRefreshQuery: Any
    publishItems: Any
    checkCompatibility: Any
    autoCompressPictures: Any
    refreshAllConnections: Any
    defaultThemeVersion: Any
    def __init__(
        self,
        date1904: Any | None = ...,
        dateCompatibility: Any | None = ...,
        showObjects: Any | None = ...,
        showBorderUnselectedTables: Any | None = ...,
        filterPrivacy: Any | None = ...,
        promptedSolutions: Any | None = ...,
        showInkAnnotation: Any | None = ...,
        backupFile: Any | None = ...,
        saveExternalLinkValues: Any | None = ...,
        updateLinks: Any | None = ...,
        codeName: Any | None = ...,
        hidePivotFieldList: Any | None = ...,
        showPivotChartFilter: Any | None = ...,
        allowRefreshQuery: Any | None = ...,
        publishItems: Any | None = ...,
        checkCompatibility: Any | None = ...,
        autoCompressPictures: Any | None = ...,
        refreshAllConnections: Any | None = ...,
        defaultThemeVersion: Any | None = ...,
    ) -> None: ...

class CalcProperties(Serialisable):
    tagname: str
    calcId: Any
    calcMode: Any
    fullCalcOnLoad: Any
    refMode: Any
    iterate: Any
    iterateCount: Any
    iterateDelta: Any
    fullPrecision: Any
    calcCompleted: Any
    calcOnSave: Any
    concurrentCalc: Any
    concurrentManualCount: Any
    forceFullCalc: Any
    def __init__(
        self,
        calcId: int = ...,
        calcMode: Any | None = ...,
        fullCalcOnLoad: bool = ...,
        refMode: Any | None = ...,
        iterate: Any | None = ...,
        iterateCount: Any | None = ...,
        iterateDelta: Any | None = ...,
        fullPrecision: Any | None = ...,
        calcCompleted: Any | None = ...,
        calcOnSave: Any | None = ...,
        concurrentCalc: Any | None = ...,
        concurrentManualCount: Any | None = ...,
        forceFullCalc: Any | None = ...,
    ) -> None: ...

class FileVersion(Serialisable):
    tagname: str
    appName: Any
    lastEdited: Any
    lowestEdited: Any
    rupBuild: Any
    codeName: Any
    def __init__(
        self,
        appName: Any | None = ...,
        lastEdited: Any | None = ...,
        lowestEdited: Any | None = ...,
        rupBuild: Any | None = ...,
        codeName: Any | None = ...,
    ) -> None: ...
