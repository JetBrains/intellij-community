from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class HierarchyUsage(Serialisable):
    tagname: str
    hierarchyUsage: Any
    def __init__(self, hierarchyUsage: Any | None = ...) -> None: ...

class ColHierarchiesUsage(Serialisable):
    tagname: str
    colHierarchyUsage: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., colHierarchyUsage=...) -> None: ...
    @property
    def count(self): ...

class RowHierarchiesUsage(Serialisable):
    tagname: str
    rowHierarchyUsage: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., rowHierarchyUsage=...) -> None: ...
    @property
    def count(self): ...

class PivotFilter(Serialisable):
    tagname: str
    fld: Any
    mpFld: Any
    type: Any
    evalOrder: Any
    id: Any
    iMeasureHier: Any
    iMeasureFld: Any
    name: Any
    description: Any
    stringValue1: Any
    stringValue2: Any
    autoFilter: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        fld: Any | None = ...,
        mpFld: Any | None = ...,
        type: Any | None = ...,
        evalOrder: Any | None = ...,
        id: Any | None = ...,
        iMeasureHier: Any | None = ...,
        iMeasureFld: Any | None = ...,
        name: Any | None = ...,
        description: Any | None = ...,
        stringValue1: Any | None = ...,
        stringValue2: Any | None = ...,
        autoFilter: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class PivotFilters(Serialisable):  # type: ignore[misc]
    count: Any
    filter: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., filter: Any | None = ...) -> None: ...

class PivotTableStyle(Serialisable):
    tagname: str
    name: Any
    showRowHeaders: Any
    showColHeaders: Any
    showRowStripes: Any
    showColStripes: Any
    showLastColumn: Any
    def __init__(
        self,
        name: Any | None = ...,
        showRowHeaders: Any | None = ...,
        showColHeaders: Any | None = ...,
        showRowStripes: Any | None = ...,
        showColStripes: Any | None = ...,
        showLastColumn: Any | None = ...,
    ) -> None: ...

class MemberList(Serialisable):
    tagname: str
    level: Any
    member: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., level: Any | None = ..., member=...) -> None: ...
    @property
    def count(self): ...

class MemberProperty(Serialisable):
    tagname: str
    name: Any
    showCell: Any
    showTip: Any
    showAsCaption: Any
    nameLen: Any
    pPos: Any
    pLen: Any
    level: Any
    field: Any
    def __init__(
        self,
        name: Any | None = ...,
        showCell: Any | None = ...,
        showTip: Any | None = ...,
        showAsCaption: Any | None = ...,
        nameLen: Any | None = ...,
        pPos: Any | None = ...,
        pLen: Any | None = ...,
        level: Any | None = ...,
        field: Any | None = ...,
    ) -> None: ...

class PivotHierarchy(Serialisable):
    tagname: str
    outline: Any
    multipleItemSelectionAllowed: Any
    subtotalTop: Any
    showInFieldList: Any
    dragToRow: Any
    dragToCol: Any
    dragToPage: Any
    dragToData: Any
    dragOff: Any
    includeNewItemsInFilter: Any
    caption: Any
    mps: Any
    members: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        outline: Any | None = ...,
        multipleItemSelectionAllowed: Any | None = ...,
        subtotalTop: Any | None = ...,
        showInFieldList: Any | None = ...,
        dragToRow: Any | None = ...,
        dragToCol: Any | None = ...,
        dragToPage: Any | None = ...,
        dragToData: Any | None = ...,
        dragOff: Any | None = ...,
        includeNewItemsInFilter: Any | None = ...,
        caption: Any | None = ...,
        mps=...,
        members: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Reference(Serialisable):
    tagname: str
    field: Any
    selected: Any
    byPosition: Any
    relative: Any
    defaultSubtotal: Any
    sumSubtotal: Any
    countASubtotal: Any
    avgSubtotal: Any
    maxSubtotal: Any
    minSubtotal: Any
    productSubtotal: Any
    countSubtotal: Any
    stdDevSubtotal: Any
    stdDevPSubtotal: Any
    varSubtotal: Any
    varPSubtotal: Any
    x: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        field: Any | None = ...,
        count: Any | None = ...,
        selected: Any | None = ...,
        byPosition: Any | None = ...,
        relative: Any | None = ...,
        defaultSubtotal: Any | None = ...,
        sumSubtotal: Any | None = ...,
        countASubtotal: Any | None = ...,
        avgSubtotal: Any | None = ...,
        maxSubtotal: Any | None = ...,
        minSubtotal: Any | None = ...,
        productSubtotal: Any | None = ...,
        countSubtotal: Any | None = ...,
        stdDevSubtotal: Any | None = ...,
        stdDevPSubtotal: Any | None = ...,
        varSubtotal: Any | None = ...,
        varPSubtotal: Any | None = ...,
        x: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
    @property
    def count(self): ...

class PivotArea(Serialisable):
    tagname: str
    references: Any
    extLst: Any
    field: Any
    type: Any
    dataOnly: Any
    labelOnly: Any
    grandRow: Any
    grandCol: Any
    cacheIndex: Any
    outline: Any
    offset: Any
    collapsedLevelsAreSubtotals: Any
    axis: Any
    fieldPosition: Any
    __elements__: Any
    def __init__(
        self,
        references=...,
        extLst: Any | None = ...,
        field: Any | None = ...,
        type: str = ...,
        dataOnly: bool = ...,
        labelOnly: Any | None = ...,
        grandRow: Any | None = ...,
        grandCol: Any | None = ...,
        cacheIndex: Any | None = ...,
        outline: bool = ...,
        offset: Any | None = ...,
        collapsedLevelsAreSubtotals: Any | None = ...,
        axis: Any | None = ...,
        fieldPosition: Any | None = ...,
    ) -> None: ...

class ChartFormat(Serialisable):
    tagname: str
    chart: Any
    format: Any
    series: Any
    pivotArea: Any
    __elements__: Any
    def __init__(
        self, chart: Any | None = ..., format: Any | None = ..., series: Any | None = ..., pivotArea: Any | None = ...
    ) -> None: ...

class ConditionalFormat(Serialisable):
    tagname: str
    scope: Any
    type: Any
    priority: Any
    pivotAreas: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        scope: Any | None = ...,
        type: Any | None = ...,
        priority: Any | None = ...,
        pivotAreas=...,
        extLst: Any | None = ...,
    ) -> None: ...

class Format(Serialisable):
    tagname: str
    action: Any
    dxfId: Any
    pivotArea: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, action: str = ..., dxfId: Any | None = ..., pivotArea: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class DataField(Serialisable):
    tagname: str
    name: Any
    fld: Any
    subtotal: Any
    showDataAs: Any
    baseField: Any
    baseItem: Any
    numFmtId: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        fld: Any | None = ...,
        subtotal: str = ...,
        showDataAs: str = ...,
        baseField: int = ...,
        baseItem: int = ...,
        numFmtId: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class PageField(Serialisable):
    tagname: str
    fld: Any
    item: Any
    hier: Any
    name: Any
    cap: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        fld: Any | None = ...,
        item: Any | None = ...,
        hier: Any | None = ...,
        name: Any | None = ...,
        cap: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class RowColItem(Serialisable):
    tagname: str
    t: Any
    r: Any
    i: Any
    x: Any
    __elements__: Any
    def __init__(self, t: str = ..., r: int = ..., i: int = ..., x=...) -> None: ...

class RowColField(Serialisable):
    tagname: str
    x: Any
    def __init__(self, x: Any | None = ...) -> None: ...

class AutoSortScope(Serialisable):  # type: ignore[misc]
    pivotArea: Any
    __elements__: Any
    def __init__(self, pivotArea: Any | None = ...) -> None: ...

class FieldItem(Serialisable):
    tagname: str
    n: Any
    t: Any
    h: Any
    s: Any
    sd: Any
    f: Any
    m: Any
    c: Any
    x: Any
    d: Any
    e: Any
    def __init__(
        self,
        n: Any | None = ...,
        t: str = ...,
        h: Any | None = ...,
        s: Any | None = ...,
        sd: bool = ...,
        f: Any | None = ...,
        m: Any | None = ...,
        c: Any | None = ...,
        x: Any | None = ...,
        d: Any | None = ...,
        e: Any | None = ...,
    ) -> None: ...

class PivotField(Serialisable):
    tagname: str
    items: Any
    autoSortScope: Any
    extLst: Any
    name: Any
    axis: Any
    dataField: Any
    subtotalCaption: Any
    showDropDowns: Any
    hiddenLevel: Any
    uniqueMemberProperty: Any
    compact: Any
    allDrilled: Any
    numFmtId: Any
    outline: Any
    subtotalTop: Any
    dragToRow: Any
    dragToCol: Any
    multipleItemSelectionAllowed: Any
    dragToPage: Any
    dragToData: Any
    dragOff: Any
    showAll: Any
    insertBlankRow: Any
    serverField: Any
    insertPageBreak: Any
    autoShow: Any
    topAutoShow: Any
    hideNewItems: Any
    measureFilter: Any
    includeNewItemsInFilter: Any
    itemPageCount: Any
    sortType: Any
    dataSourceSort: Any
    nonAutoSortDefault: Any
    rankBy: Any
    defaultSubtotal: Any
    sumSubtotal: Any
    countASubtotal: Any
    avgSubtotal: Any
    maxSubtotal: Any
    minSubtotal: Any
    productSubtotal: Any
    countSubtotal: Any
    stdDevSubtotal: Any
    stdDevPSubtotal: Any
    varSubtotal: Any
    varPSubtotal: Any
    showPropCell: Any
    showPropTip: Any
    showPropAsCaption: Any
    defaultAttributeDrillState: Any
    __elements__: Any
    def __init__(
        self,
        items=...,
        autoSortScope: Any | None = ...,
        name: Any | None = ...,
        axis: Any | None = ...,
        dataField: Any | None = ...,
        subtotalCaption: Any | None = ...,
        showDropDowns: bool = ...,
        hiddenLevel: Any | None = ...,
        uniqueMemberProperty: Any | None = ...,
        compact: bool = ...,
        allDrilled: Any | None = ...,
        numFmtId: Any | None = ...,
        outline: bool = ...,
        subtotalTop: bool = ...,
        dragToRow: bool = ...,
        dragToCol: bool = ...,
        multipleItemSelectionAllowed: Any | None = ...,
        dragToPage: bool = ...,
        dragToData: bool = ...,
        dragOff: bool = ...,
        showAll: bool = ...,
        insertBlankRow: Any | None = ...,
        serverField: Any | None = ...,
        insertPageBreak: Any | None = ...,
        autoShow: Any | None = ...,
        topAutoShow: bool = ...,
        hideNewItems: Any | None = ...,
        measureFilter: Any | None = ...,
        includeNewItemsInFilter: Any | None = ...,
        itemPageCount: int = ...,
        sortType: str = ...,
        dataSourceSort: Any | None = ...,
        nonAutoSortDefault: Any | None = ...,
        rankBy: Any | None = ...,
        defaultSubtotal: bool = ...,
        sumSubtotal: Any | None = ...,
        countASubtotal: Any | None = ...,
        avgSubtotal: Any | None = ...,
        maxSubtotal: Any | None = ...,
        minSubtotal: Any | None = ...,
        productSubtotal: Any | None = ...,
        countSubtotal: Any | None = ...,
        stdDevSubtotal: Any | None = ...,
        stdDevPSubtotal: Any | None = ...,
        varSubtotal: Any | None = ...,
        varPSubtotal: Any | None = ...,
        showPropCell: Any | None = ...,
        showPropTip: Any | None = ...,
        showPropAsCaption: Any | None = ...,
        defaultAttributeDrillState: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class Location(Serialisable):
    tagname: str
    ref: Any
    firstHeaderRow: Any
    firstDataRow: Any
    firstDataCol: Any
    rowPageCount: Any
    colPageCount: Any
    def __init__(
        self,
        ref: Any | None = ...,
        firstHeaderRow: Any | None = ...,
        firstDataRow: Any | None = ...,
        firstDataCol: Any | None = ...,
        rowPageCount: Any | None = ...,
        colPageCount: Any | None = ...,
    ) -> None: ...

class TableDefinition(Serialisable):
    mime_type: str
    rel_type: str
    tagname: str
    cache: Any
    name: Any
    cacheId: Any
    dataOnRows: Any
    dataPosition: Any
    dataCaption: Any
    grandTotalCaption: Any
    errorCaption: Any
    showError: Any
    missingCaption: Any
    showMissing: Any
    pageStyle: Any
    pivotTableStyle: Any
    vacatedStyle: Any
    tag: Any
    updatedVersion: Any
    minRefreshableVersion: Any
    asteriskTotals: Any
    showItems: Any
    editData: Any
    disableFieldList: Any
    showCalcMbrs: Any
    visualTotals: Any
    showMultipleLabel: Any
    showDataDropDown: Any
    showDrill: Any
    printDrill: Any
    showMemberPropertyTips: Any
    showDataTips: Any
    enableWizard: Any
    enableDrill: Any
    enableFieldProperties: Any
    preserveFormatting: Any
    useAutoFormatting: Any
    pageWrap: Any
    pageOverThenDown: Any
    subtotalHiddenItems: Any
    rowGrandTotals: Any
    colGrandTotals: Any
    fieldPrintTitles: Any
    itemPrintTitles: Any
    mergeItem: Any
    showDropZones: Any
    createdVersion: Any
    indent: Any
    showEmptyRow: Any
    showEmptyCol: Any
    showHeaders: Any
    compact: Any
    outline: Any
    outlineData: Any
    compactData: Any
    published: Any
    gridDropZones: Any
    immersive: Any
    multipleFieldFilters: Any
    chartFormat: Any
    rowHeaderCaption: Any
    colHeaderCaption: Any
    fieldListSortAscending: Any
    mdxSubqueries: Any
    customListSort: Any
    autoFormatId: Any
    applyNumberFormats: Any
    applyBorderFormats: Any
    applyFontFormats: Any
    applyPatternFormats: Any
    applyAlignmentFormats: Any
    applyWidthHeightFormats: Any
    location: Any
    pivotFields: Any
    rowFields: Any
    rowItems: Any
    colFields: Any
    colItems: Any
    pageFields: Any
    dataFields: Any
    formats: Any
    conditionalFormats: Any
    chartFormats: Any
    pivotHierarchies: Any
    pivotTableStyleInfo: Any
    filters: Any
    rowHierarchiesUsage: Any
    colHierarchiesUsage: Any
    extLst: Any
    id: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        cacheId: Any | None = ...,
        dataOnRows: bool = ...,
        dataPosition: Any | None = ...,
        dataCaption: Any | None = ...,
        grandTotalCaption: Any | None = ...,
        errorCaption: Any | None = ...,
        showError: bool = ...,
        missingCaption: Any | None = ...,
        showMissing: bool = ...,
        pageStyle: Any | None = ...,
        pivotTableStyle: Any | None = ...,
        vacatedStyle: Any | None = ...,
        tag: Any | None = ...,
        updatedVersion: int = ...,
        minRefreshableVersion: int = ...,
        asteriskTotals: bool = ...,
        showItems: bool = ...,
        editData: bool = ...,
        disableFieldList: bool = ...,
        showCalcMbrs: bool = ...,
        visualTotals: bool = ...,
        showMultipleLabel: bool = ...,
        showDataDropDown: bool = ...,
        showDrill: bool = ...,
        printDrill: bool = ...,
        showMemberPropertyTips: bool = ...,
        showDataTips: bool = ...,
        enableWizard: bool = ...,
        enableDrill: bool = ...,
        enableFieldProperties: bool = ...,
        preserveFormatting: bool = ...,
        useAutoFormatting: bool = ...,
        pageWrap: int = ...,
        pageOverThenDown: bool = ...,
        subtotalHiddenItems: bool = ...,
        rowGrandTotals: bool = ...,
        colGrandTotals: bool = ...,
        fieldPrintTitles: bool = ...,
        itemPrintTitles: bool = ...,
        mergeItem: bool = ...,
        showDropZones: bool = ...,
        createdVersion: int = ...,
        indent: int = ...,
        showEmptyRow: bool = ...,
        showEmptyCol: bool = ...,
        showHeaders: bool = ...,
        compact: bool = ...,
        outline: bool = ...,
        outlineData: bool = ...,
        compactData: bool = ...,
        published: bool = ...,
        gridDropZones: bool = ...,
        immersive: bool = ...,
        multipleFieldFilters: Any | None = ...,
        chartFormat: int = ...,
        rowHeaderCaption: Any | None = ...,
        colHeaderCaption: Any | None = ...,
        fieldListSortAscending: Any | None = ...,
        mdxSubqueries: Any | None = ...,
        customListSort: Any | None = ...,
        autoFormatId: Any | None = ...,
        applyNumberFormats: bool = ...,
        applyBorderFormats: bool = ...,
        applyFontFormats: bool = ...,
        applyPatternFormats: bool = ...,
        applyAlignmentFormats: bool = ...,
        applyWidthHeightFormats: bool = ...,
        location: Any | None = ...,
        pivotFields=...,
        rowFields=...,
        rowItems=...,
        colFields=...,
        colItems=...,
        pageFields=...,
        dataFields=...,
        formats=...,
        conditionalFormats=...,
        chartFormats=...,
        pivotHierarchies=...,
        pivotTableStyleInfo: Any | None = ...,
        filters=...,
        rowHierarchiesUsage: Any | None = ...,
        colHierarchiesUsage: Any | None = ...,
        extLst: Any | None = ...,
        id: Any | None = ...,
    ) -> None: ...
    def to_tree(self): ...
    @property
    def path(self): ...
