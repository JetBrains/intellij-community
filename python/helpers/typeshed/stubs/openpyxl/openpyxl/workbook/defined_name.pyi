from collections.abc import Generator
from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

RESERVED: Any
RESERVED_REGEX: Any
COL_RANGE: str
COL_RANGE_RE: Any
ROW_RANGE: str
ROW_RANGE_RE: Any
TITLES_REGEX: Any

class DefinedName(Serialisable):
    tagname: str
    name: Any
    comment: Any
    customMenu: Any
    description: Any
    help: Any
    statusBar: Any
    localSheetId: Any
    hidden: Any
    function: Any
    vbProcedure: Any
    xlm: Any
    functionGroupId: Any
    shortcutKey: Any
    publishToServer: Any
    workbookParameter: Any
    attr_text: Any
    value: Any
    def __init__(
        self,
        name: Any | None = ...,
        comment: Any | None = ...,
        customMenu: Any | None = ...,
        description: Any | None = ...,
        help: Any | None = ...,
        statusBar: Any | None = ...,
        localSheetId: Any | None = ...,
        hidden: Any | None = ...,
        function: Any | None = ...,
        vbProcedure: Any | None = ...,
        xlm: Any | None = ...,
        functionGroupId: Any | None = ...,
        shortcutKey: Any | None = ...,
        publishToServer: Any | None = ...,
        workbookParameter: Any | None = ...,
        attr_text: Any | None = ...,
    ) -> None: ...
    @property
    def type(self): ...
    @property
    def destinations(self) -> Generator[Any, None, None]: ...
    @property
    def is_reserved(self): ...
    @property
    def is_external(self): ...
    def __iter__(self): ...

class DefinedNameList(Serialisable):
    tagname: str
    definedName: Any
    def __init__(self, definedName=...) -> None: ...
    def append(self, defn) -> None: ...
    def __len__(self): ...
    def __contains__(self, name): ...
    def __getitem__(self, name): ...
    def get(self, name, scope: Any | None = ...): ...
    def __delitem__(self, name) -> None: ...
    def delete(self, name, scope: Any | None = ...): ...
    def localnames(self, scope): ...
