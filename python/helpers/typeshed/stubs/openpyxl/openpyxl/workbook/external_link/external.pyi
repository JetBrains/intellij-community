from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ExternalCell(Serialisable):  # type: ignore[misc]
    r: Any
    t: Any
    vm: Any
    v: Any
    def __init__(self, r: Any | None = ..., t: Any | None = ..., vm: Any | None = ..., v: Any | None = ...) -> None: ...

class ExternalRow(Serialisable):  # type: ignore[misc]
    r: Any
    cell: Any
    __elements__: Any
    def __init__(self, r=..., cell: Any | None = ...) -> None: ...

class ExternalSheetData(Serialisable):  # type: ignore[misc]
    sheetId: Any
    refreshError: Any
    row: Any
    __elements__: Any
    def __init__(self, sheetId: Any | None = ..., refreshError: Any | None = ..., row=...) -> None: ...

class ExternalSheetDataSet(Serialisable):  # type: ignore[misc]
    sheetData: Any
    __elements__: Any
    def __init__(self, sheetData: Any | None = ...) -> None: ...

class ExternalSheetNames(Serialisable):  # type: ignore[misc]
    sheetName: Any
    __elements__: Any
    def __init__(self, sheetName=...) -> None: ...

class ExternalDefinedName(Serialisable):
    tagname: str
    name: Any
    refersTo: Any
    sheetId: Any
    def __init__(self, name: Any | None = ..., refersTo: Any | None = ..., sheetId: Any | None = ...) -> None: ...

class ExternalBook(Serialisable):
    tagname: str
    sheetNames: Any
    definedNames: Any
    sheetDataSet: Any
    id: Any
    __elements__: Any
    def __init__(
        self, sheetNames: Any | None = ..., definedNames=..., sheetDataSet: Any | None = ..., id: Any | None = ...
    ) -> None: ...

class ExternalLink(Serialisable):
    tagname: str
    mime_type: str
    externalBook: Any
    file_link: Any
    __elements__: Any
    def __init__(
        self, externalBook: Any | None = ..., ddeLink: Any | None = ..., oleLink: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...
    def to_tree(self): ...
    @property
    def path(self): ...

def read_external_link(archive, book_path): ...
