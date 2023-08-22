from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class _Protected:
    def set_password(self, value: str = ..., already_hashed: bool = ...) -> None: ...
    @property
    def password(self): ...
    @password.setter
    def password(self, value) -> None: ...

class SheetProtection(Serialisable, _Protected):
    tagname: str
    sheet: Any
    enabled: Any
    objects: Any
    scenarios: Any
    formatCells: Any
    formatColumns: Any
    formatRows: Any
    insertColumns: Any
    insertRows: Any
    insertHyperlinks: Any
    deleteColumns: Any
    deleteRows: Any
    selectLockedCells: Any
    selectUnlockedCells: Any
    sort: Any
    autoFilter: Any
    pivotTables: Any
    saltValue: Any
    spinCount: Any
    algorithmName: Any
    hashValue: Any
    __attrs__: Any
    password: Any
    def __init__(
        self,
        sheet: bool = ...,
        objects: bool = ...,
        scenarios: bool = ...,
        formatCells: bool = ...,
        formatRows: bool = ...,
        formatColumns: bool = ...,
        insertColumns: bool = ...,
        insertRows: bool = ...,
        insertHyperlinks: bool = ...,
        deleteColumns: bool = ...,
        deleteRows: bool = ...,
        selectLockedCells: bool = ...,
        selectUnlockedCells: bool = ...,
        sort: bool = ...,
        autoFilter: bool = ...,
        pivotTables: bool = ...,
        password: Any | None = ...,
        algorithmName: Any | None = ...,
        saltValue: Any | None = ...,
        spinCount: Any | None = ...,
        hashValue: Any | None = ...,
    ) -> None: ...
    def set_password(self, value: str = ..., already_hashed: bool = ...) -> None: ...
    def enable(self) -> None: ...
    def disable(self) -> None: ...
    def __bool__(self): ...
