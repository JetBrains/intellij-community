from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class MeasureDimensionMap(Serialisable):
    tagname: str
    measureGroup: Any
    dimension: Any
    def __init__(self, measureGroup: Any | None = ..., dimension: Any | None = ...) -> None: ...

class MeasureGroup(Serialisable):
    tagname: str
    name: Any
    caption: Any
    def __init__(self, name: Any | None = ..., caption: Any | None = ...) -> None: ...

class PivotDimension(Serialisable):
    tagname: str
    measure: Any
    name: Any
    uniqueName: Any
    caption: Any
    def __init__(
        self, measure: Any | None = ..., name: Any | None = ..., uniqueName: Any | None = ..., caption: Any | None = ...
    ) -> None: ...

class CalculatedMember(Serialisable):
    tagname: str
    name: Any
    mdx: Any
    memberName: Any
    hierarchy: Any
    parent: Any
    solveOrder: Any
    set: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        mdx: Any | None = ...,
        memberName: Any | None = ...,
        hierarchy: Any | None = ...,
        parent: Any | None = ...,
        solveOrder: Any | None = ...,
        set: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class CalculatedItem(Serialisable):
    tagname: str
    field: Any
    formula: Any
    pivotArea: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, field: Any | None = ..., formula: Any | None = ..., pivotArea: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...

class ServerFormat(Serialisable):
    tagname: str
    culture: Any
    format: Any
    def __init__(self, culture: Any | None = ..., format: Any | None = ...) -> None: ...

class ServerFormatList(Serialisable):
    tagname: str
    serverFormat: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., serverFormat: Any | None = ...) -> None: ...
    @property
    def count(self): ...

class Query(Serialisable):
    tagname: str
    mdx: Any
    tpls: Any
    __elements__: Any
    def __init__(self, mdx: Any | None = ..., tpls: Any | None = ...) -> None: ...

class QueryCache(Serialisable):
    tagname: str
    count: Any
    query: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., query: Any | None = ...) -> None: ...

class OLAPSet(Serialisable):
    tagname: str
    count: Any
    maxRank: Any
    setDefinition: Any
    sortType: Any
    queryFailed: Any
    tpls: Any
    sortByTuple: Any
    __elements__: Any
    def __init__(
        self,
        count: Any | None = ...,
        maxRank: Any | None = ...,
        setDefinition: Any | None = ...,
        sortType: Any | None = ...,
        queryFailed: Any | None = ...,
        tpls: Any | None = ...,
        sortByTuple: Any | None = ...,
    ) -> None: ...

class OLAPSets(Serialisable):  # type: ignore[misc]
    count: Any
    set: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., set: Any | None = ...) -> None: ...

class PCDSDTCEntries(Serialisable):
    tagname: str
    count: Any
    m: Any
    n: Any
    e: Any
    s: Any
    __elements__: Any
    def __init__(
        self, count: Any | None = ..., m: Any | None = ..., n: Any | None = ..., e: Any | None = ..., s: Any | None = ...
    ) -> None: ...

class TupleCache(Serialisable):
    tagname: str
    entries: Any
    sets: Any
    queryCache: Any
    serverFormats: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        entries: Any | None = ...,
        sets: Any | None = ...,
        queryCache: Any | None = ...,
        serverFormats: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class PCDKPI(Serialisable):
    tagname: str
    uniqueName: Any
    caption: Any
    displayFolder: Any
    measureGroup: Any
    parent: Any
    value: Any
    goal: Any
    status: Any
    trend: Any
    weight: Any
    time: Any
    def __init__(
        self,
        uniqueName: Any | None = ...,
        caption: Any | None = ...,
        displayFolder: Any | None = ...,
        measureGroup: Any | None = ...,
        parent: Any | None = ...,
        value: Any | None = ...,
        goal: Any | None = ...,
        status: Any | None = ...,
        trend: Any | None = ...,
        weight: Any | None = ...,
        time: Any | None = ...,
    ) -> None: ...

class GroupMember(Serialisable):
    tagname: str
    uniqueName: Any
    group: Any
    def __init__(self, uniqueName: Any | None = ..., group: Any | None = ...) -> None: ...

class GroupMembers(Serialisable):  # type: ignore[misc]
    count: Any
    groupMember: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., groupMember: Any | None = ...) -> None: ...

class LevelGroup(Serialisable):
    tagname: str
    name: Any
    uniqueName: Any
    caption: Any
    uniqueParent: Any
    id: Any
    groupMembers: Any
    __elements__: Any
    def __init__(
        self,
        name: Any | None = ...,
        uniqueName: Any | None = ...,
        caption: Any | None = ...,
        uniqueParent: Any | None = ...,
        id: Any | None = ...,
        groupMembers: Any | None = ...,
    ) -> None: ...

class Groups(Serialisable):
    tagname: str
    count: Any
    group: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., group: Any | None = ...) -> None: ...

class GroupLevel(Serialisable):
    tagname: str
    uniqueName: Any
    caption: Any
    user: Any
    customRollUp: Any
    groups: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        uniqueName: Any | None = ...,
        caption: Any | None = ...,
        user: Any | None = ...,
        customRollUp: Any | None = ...,
        groups: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class GroupLevels(Serialisable):  # type: ignore[misc]
    count: Any
    groupLevel: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., groupLevel: Any | None = ...) -> None: ...

class FieldUsage(Serialisable):
    tagname: str
    x: Any
    def __init__(self, x: Any | None = ...) -> None: ...

class FieldsUsage(Serialisable):  # type: ignore[misc]
    count: Any
    fieldUsage: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., fieldUsage: Any | None = ...) -> None: ...

class CacheHierarchy(Serialisable):
    tagname: str
    uniqueName: Any
    caption: Any
    measure: Any
    set: Any
    parentSet: Any
    iconSet: Any
    attribute: Any
    time: Any
    keyAttribute: Any
    defaultMemberUniqueName: Any
    allUniqueName: Any
    allCaption: Any
    dimensionUniqueName: Any
    displayFolder: Any
    measureGroup: Any
    measures: Any
    count: Any
    oneField: Any
    memberValueDatatype: Any
    unbalanced: Any
    unbalancedGroup: Any
    hidden: Any
    fieldsUsage: Any
    groupLevels: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        uniqueName: str = ...,
        caption: Any | None = ...,
        measure: Any | None = ...,
        set: Any | None = ...,
        parentSet: Any | None = ...,
        iconSet: int = ...,
        attribute: Any | None = ...,
        time: Any | None = ...,
        keyAttribute: Any | None = ...,
        defaultMemberUniqueName: Any | None = ...,
        allUniqueName: Any | None = ...,
        allCaption: Any | None = ...,
        dimensionUniqueName: Any | None = ...,
        displayFolder: Any | None = ...,
        measureGroup: Any | None = ...,
        measures: Any | None = ...,
        count: Any | None = ...,
        oneField: Any | None = ...,
        memberValueDatatype: Any | None = ...,
        unbalanced: Any | None = ...,
        unbalancedGroup: Any | None = ...,
        hidden: Any | None = ...,
        fieldsUsage: Any | None = ...,
        groupLevels: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class GroupItems(Serialisable):
    tagname: str
    m: Any
    n: Any
    b: Any
    e: Any
    s: Any
    d: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., m=..., n=..., b=..., e=..., s=..., d=...) -> None: ...
    @property
    def count(self): ...

class DiscretePr(Serialisable):
    tagname: str
    count: Any
    x: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., x: Any | None = ...) -> None: ...

class RangePr(Serialisable):
    tagname: str
    autoStart: Any
    autoEnd: Any
    groupBy: Any
    startNum: Any
    endNum: Any
    startDate: Any
    endDate: Any
    groupInterval: Any
    def __init__(
        self,
        autoStart: bool = ...,
        autoEnd: bool = ...,
        groupBy: str = ...,
        startNum: Any | None = ...,
        endNum: Any | None = ...,
        startDate: Any | None = ...,
        endDate: Any | None = ...,
        groupInterval: int = ...,
    ) -> None: ...

class FieldGroup(Serialisable):
    tagname: str
    par: Any
    base: Any
    rangePr: Any
    discretePr: Any
    groupItems: Any
    __elements__: Any
    def __init__(
        self,
        par: Any | None = ...,
        base: Any | None = ...,
        rangePr: Any | None = ...,
        discretePr: Any | None = ...,
        groupItems: Any | None = ...,
    ) -> None: ...

class SharedItems(Serialisable):
    tagname: str
    m: Any
    n: Any
    b: Any
    e: Any
    s: Any
    d: Any
    containsSemiMixedTypes: Any
    containsNonDate: Any
    containsDate: Any
    containsString: Any
    containsBlank: Any
    containsMixedTypes: Any
    containsNumber: Any
    containsInteger: Any
    minValue: Any
    maxValue: Any
    minDate: Any
    maxDate: Any
    longText: Any
    __attrs__: Any
    def __init__(
        self,
        _fields=...,
        containsSemiMixedTypes: Any | None = ...,
        containsNonDate: Any | None = ...,
        containsDate: Any | None = ...,
        containsString: Any | None = ...,
        containsBlank: Any | None = ...,
        containsMixedTypes: Any | None = ...,
        containsNumber: Any | None = ...,
        containsInteger: Any | None = ...,
        minValue: Any | None = ...,
        maxValue: Any | None = ...,
        minDate: Any | None = ...,
        maxDate: Any | None = ...,
        count: Any | None = ...,
        longText: Any | None = ...,
    ) -> None: ...
    @property
    def count(self): ...

class CacheField(Serialisable):
    tagname: str
    sharedItems: Any
    fieldGroup: Any
    mpMap: Any
    extLst: Any
    name: Any
    caption: Any
    propertyName: Any
    serverField: Any
    uniqueList: Any
    numFmtId: Any
    formula: Any
    sqlType: Any
    hierarchy: Any
    level: Any
    databaseField: Any
    mappingCount: Any
    memberPropertyField: Any
    __elements__: Any
    def __init__(
        self,
        sharedItems: Any | None = ...,
        fieldGroup: Any | None = ...,
        mpMap: Any | None = ...,
        extLst: Any | None = ...,
        name: Any | None = ...,
        caption: Any | None = ...,
        propertyName: Any | None = ...,
        serverField: Any | None = ...,
        uniqueList: bool = ...,
        numFmtId: Any | None = ...,
        formula: Any | None = ...,
        sqlType: int = ...,
        hierarchy: int = ...,
        level: int = ...,
        databaseField: bool = ...,
        mappingCount: Any | None = ...,
        memberPropertyField: Any | None = ...,
    ) -> None: ...

class RangeSet(Serialisable):
    tagname: str
    i1: Any
    i2: Any
    i3: Any
    i4: Any
    ref: Any
    name: Any
    sheet: Any
    def __init__(
        self,
        i1: Any | None = ...,
        i2: Any | None = ...,
        i3: Any | None = ...,
        i4: Any | None = ...,
        ref: Any | None = ...,
        name: Any | None = ...,
        sheet: Any | None = ...,
    ) -> None: ...

class PageItem(Serialisable):
    tagname: str
    name: Any
    def __init__(self, name: Any | None = ...) -> None: ...

class Page(Serialisable):
    tagname: str
    pageItem: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., pageItem: Any | None = ...) -> None: ...
    @property
    def count(self): ...

class Consolidation(Serialisable):
    tagname: str
    autoPage: Any
    pages: Any
    rangeSets: Any
    __elements__: Any
    def __init__(self, autoPage: Any | None = ..., pages=..., rangeSets=...) -> None: ...

class WorksheetSource(Serialisable):
    tagname: str
    ref: Any
    name: Any
    sheet: Any
    def __init__(self, ref: Any | None = ..., name: Any | None = ..., sheet: Any | None = ...) -> None: ...

class CacheSource(Serialisable):
    tagname: str
    type: Any
    connectionId: Any
    worksheetSource: Any
    consolidation: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        type: Any | None = ...,
        connectionId: Any | None = ...,
        worksheetSource: Any | None = ...,
        consolidation: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class CacheDefinition(Serialisable):
    mime_type: str
    rel_type: str
    records: Any
    tagname: str
    invalid: Any
    saveData: Any
    refreshOnLoad: Any
    optimizeMemory: Any
    enableRefresh: Any
    refreshedBy: Any
    refreshedDate: Any
    refreshedDateIso: Any
    backgroundQuery: Any
    missingItemsLimit: Any
    createdVersion: Any
    refreshedVersion: Any
    minRefreshableVersion: Any
    recordCount: Any
    upgradeOnRefresh: Any
    tupleCache: Any
    supportSubquery: Any
    supportAdvancedDrill: Any
    cacheSource: Any
    cacheFields: Any
    cacheHierarchies: Any
    kpis: Any
    calculatedItems: Any
    calculatedMembers: Any
    dimensions: Any
    measureGroups: Any
    maps: Any
    extLst: Any
    id: Any
    __elements__: Any
    def __init__(
        self,
        invalid: Any | None = ...,
        saveData: Any | None = ...,
        refreshOnLoad: Any | None = ...,
        optimizeMemory: Any | None = ...,
        enableRefresh: Any | None = ...,
        refreshedBy: Any | None = ...,
        refreshedDate: Any | None = ...,
        refreshedDateIso: Any | None = ...,
        backgroundQuery: Any | None = ...,
        missingItemsLimit: Any | None = ...,
        createdVersion: Any | None = ...,
        refreshedVersion: Any | None = ...,
        minRefreshableVersion: Any | None = ...,
        recordCount: Any | None = ...,
        upgradeOnRefresh: Any | None = ...,
        tupleCache: Any | None = ...,
        supportSubquery: Any | None = ...,
        supportAdvancedDrill: Any | None = ...,
        cacheSource: Any | None = ...,
        cacheFields=...,
        cacheHierarchies=...,
        kpis=...,
        calculatedItems=...,
        calculatedMembers=...,
        dimensions=...,
        measureGroups=...,
        maps=...,
        extLst: Any | None = ...,
        id: Any | None = ...,
    ) -> None: ...
    def to_tree(self): ...
    @property
    def path(self): ...
