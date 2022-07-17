from typing import Any, NamedTuple

from .syntax import PDFObject

class MarkedContent(NamedTuple):
    page_object_id: int
    struct_parents_id: int
    struct_type: str
    mcid: int | None = ...
    title: str | None = ...
    alt_text: str | None = ...

class NumberTree(PDFObject):
    nums: Any
    def __init__(self, **kwargs) -> None: ...
    def serialize(self, fpdf: Any | None = ..., obj_dict: Any | None = ...): ...

class StructTreeRoot(PDFObject):
    type: str
    parent_tree: Any
    k: Any
    def __init__(self, **kwargs) -> None: ...

class StructElem(PDFObject):
    type: str
    s: Any
    p: Any
    k: Any
    pg: Any
    t: Any
    alt: Any
    def __init__(
        self,
        struct_type: str,
        parent: PDFObject,
        kids: list[int] | list[StructElem],
        page: PDFObject | None = ...,
        title: str | None = ...,
        alt: str | None = ...,
        **kwargs,
    ) -> None: ...

class StructureTreeBuilder:
    struct_tree_root: Any
    doc_struct_elem: Any
    struct_elem_per_mc: Any
    def __init__(self) -> None: ...
    def add_marked_content(self, marked_content) -> None: ...
    def next_mcid_for_page(self, page_object_id): ...
    def empty(self): ...
    def serialize(self, first_object_id: int = ..., fpdf: Any | None = ...): ...
    def assign_ids(self, n): ...
