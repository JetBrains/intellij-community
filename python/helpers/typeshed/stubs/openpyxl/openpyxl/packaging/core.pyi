from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors import DateTime
from openpyxl.descriptors.base import Alias
from openpyxl.descriptors.nested import NestedText
from openpyxl.descriptors.serialisable import Serialisable

# Does not reimplement the relevant methods, so runtime also has incompatible supertypes
class NestedDateTime(DateTime[Incomplete], NestedText[Incomplete, Incomplete]):  # type: ignore[misc]
    expected_type: type[Incomplete]
    def to_tree(self, tagname: str | None = None, value: Incomplete | None = None, namespace: str | None = None): ...

class QualifiedDateTime(NestedDateTime):
    def to_tree(self, tagname: str | None = None, value: Incomplete | None = None, namespace: str | None = None): ...

class DocumentProperties(Serialisable):
    tagname: ClassVar[str]
    namespace: ClassVar[str]
    category: NestedText[str, Literal[True]]
    contentStatus: NestedText[str, Literal[True]]
    keywords: NestedText[str, Literal[True]]
    lastModifiedBy: NestedText[str, Literal[True]]
    lastPrinted: Incomplete
    revision: NestedText[str, Literal[True]]
    version: NestedText[str, Literal[True]]
    last_modified_by: Alias
    subject: NestedText[str, Literal[True]]
    title: NestedText[str, Literal[True]]
    creator: NestedText[str, Literal[True]]
    description: NestedText[str, Literal[True]]
    identifier: NestedText[str, Literal[True]]
    language: NestedText[str, Literal[True]]
    created: Incomplete
    modified: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        category: object = None,
        contentStatus: object = None,
        keywords: object = None,
        lastModifiedBy: object = None,
        lastPrinted: Incomplete | None = None,
        revision: object = None,
        version: object = None,
        created=None,
        creator: object = "openpyxl",
        description: object = None,
        identifier: object = None,
        language: object = None,
        modified=None,
        subject: object = None,
        title: object = None,
    ) -> None: ...
