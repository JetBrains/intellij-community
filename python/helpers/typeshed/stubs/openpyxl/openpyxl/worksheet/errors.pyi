from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Extension(Serialisable):
    tagname: str
    uri: Any
    def __init__(self, uri: Any | None = ...) -> None: ...

class ExtensionList(Serialisable):
    tagname: str
    ext: Any
    __elements__: Any
    def __init__(self, ext=...) -> None: ...

class IgnoredError(Serialisable):
    tagname: str
    sqref: Any
    evalError: Any
    twoDigitTextYear: Any
    numberStoredAsText: Any
    formula: Any
    formulaRange: Any
    unlockedFormula: Any
    emptyCellReference: Any
    listDataValidation: Any
    calculatedColumn: Any
    def __init__(
        self,
        sqref: Any | None = ...,
        evalError: bool = ...,
        twoDigitTextYear: bool = ...,
        numberStoredAsText: bool = ...,
        formula: bool = ...,
        formulaRange: bool = ...,
        unlockedFormula: bool = ...,
        emptyCellReference: bool = ...,
        listDataValidation: bool = ...,
        calculatedColumn: bool = ...,
    ) -> None: ...

class IgnoredErrors(Serialisable):
    tagname: str
    ignoredError: Any
    extLst: Any
    __elements__: Any
    def __init__(self, ignoredError=..., extLst: Any | None = ...) -> None: ...
