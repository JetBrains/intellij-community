from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class WorkbookProtection(Serialisable):
    tagname: str
    workbook_password: Any
    workbookPasswordCharacterSet: Any
    revision_password: Any
    revisionsPasswordCharacterSet: Any
    lockStructure: Any
    lock_structure: Any
    lockWindows: Any
    lock_windows: Any
    lockRevision: Any
    lock_revision: Any
    revisionsAlgorithmName: Any
    revisionsHashValue: Any
    revisionsSaltValue: Any
    revisionsSpinCount: Any
    workbookAlgorithmName: Any
    workbookHashValue: Any
    workbookSaltValue: Any
    workbookSpinCount: Any
    __attrs__: Any
    def __init__(
        self,
        workbookPassword: Any | None = ...,
        workbookPasswordCharacterSet: Any | None = ...,
        revisionsPassword: Any | None = ...,
        revisionsPasswordCharacterSet: Any | None = ...,
        lockStructure: Any | None = ...,
        lockWindows: Any | None = ...,
        lockRevision: Any | None = ...,
        revisionsAlgorithmName: Any | None = ...,
        revisionsHashValue: Any | None = ...,
        revisionsSaltValue: Any | None = ...,
        revisionsSpinCount: Any | None = ...,
        workbookAlgorithmName: Any | None = ...,
        workbookHashValue: Any | None = ...,
        workbookSaltValue: Any | None = ...,
        workbookSpinCount: Any | None = ...,
    ) -> None: ...
    def set_workbook_password(self, value: str = ..., already_hashed: bool = ...) -> None: ...
    @property
    def workbookPassword(self): ...
    @workbookPassword.setter
    def workbookPassword(self, value) -> None: ...
    def set_revisions_password(self, value: str = ..., already_hashed: bool = ...) -> None: ...
    @property
    def revisionsPassword(self): ...
    @revisionsPassword.setter
    def revisionsPassword(self, value) -> None: ...
    @classmethod
    def from_tree(cls, node): ...

DocumentSecurity = WorkbookProtection

class FileSharing(Serialisable):
    tagname: str
    readOnlyRecommended: Any
    userName: Any
    reservationPassword: Any
    algorithmName: Any
    hashValue: Any
    saltValue: Any
    spinCount: Any
    def __init__(
        self,
        readOnlyRecommended: Any | None = ...,
        userName: Any | None = ...,
        reservationPassword: Any | None = ...,
        algorithmName: Any | None = ...,
        hashValue: Any | None = ...,
        saltValue: Any | None = ...,
        spinCount: Any | None = ...,
    ) -> None: ...
