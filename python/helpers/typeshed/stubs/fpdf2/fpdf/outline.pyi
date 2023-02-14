from typing import Any, NamedTuple

from .structure_tree import StructElem
from .syntax import Destination, PDFObject

class OutlineSection(NamedTuple):
    name: str
    level: str
    page_number: int
    dest: Destination
    struct_elem: StructElem | None = ...

class OutlineItemDictionary(PDFObject):
    title: str
    parent: Any | None
    prev: Any | None
    next: Any | None
    first: Any | None
    last: Any | None
    count: int
    dest: str | None
    struct_elem: StructElem | None
    def __init__(self, title: str, dest: str | None = ..., struct_elem: StructElem | None = ..., **kwargs) -> None: ...

class OutlineDictionary(PDFObject):
    type: str
    first: Any | None
    last: Any | None
    count: int
    def __init__(self, **kwargs) -> None: ...

def serialize_outline(sections, first_object_id: int = ..., fpdf: Any | None = ...): ...
