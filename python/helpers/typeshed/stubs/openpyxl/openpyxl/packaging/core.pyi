from typing import Any

from openpyxl.descriptors import DateTime
from openpyxl.descriptors.nested import NestedText
from openpyxl.descriptors.serialisable import Serialisable

class NestedDateTime(DateTime, NestedText):
    expected_type: Any
    def to_tree(self, tagname: Any | None = ..., value: Any | None = ..., namespace: Any | None = ...): ...

class QualifiedDateTime(NestedDateTime):
    def to_tree(self, tagname: Any | None = ..., value: Any | None = ..., namespace: Any | None = ...): ...

class DocumentProperties(Serialisable):
    tagname: str
    namespace: Any
    category: Any
    contentStatus: Any
    keywords: Any
    lastModifiedBy: Any
    lastPrinted: Any
    revision: Any
    version: Any
    last_modified_by: Any
    subject: Any
    title: Any
    creator: Any
    description: Any
    identifier: Any
    language: Any
    created: Any
    modified: Any
    __elements__: Any
    def __init__(
        self,
        category: Any | None = ...,
        contentStatus: Any | None = ...,
        keywords: Any | None = ...,
        lastModifiedBy: Any | None = ...,
        lastPrinted: Any | None = ...,
        revision: Any | None = ...,
        version: Any | None = ...,
        created=...,
        creator: str = ...,
        description: Any | None = ...,
        identifier: Any | None = ...,
        language: Any | None = ...,
        modified=...,
        subject: Any | None = ...,
        title: Any | None = ...,
    ) -> None: ...
