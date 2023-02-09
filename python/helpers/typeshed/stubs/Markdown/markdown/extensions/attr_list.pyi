from re import Pattern

from markdown.extensions import Extension
from markdown.treeprocessors import Treeprocessor

def get_attrs(str): ...
def isheader(elem): ...

class AttrListTreeprocessor(Treeprocessor):
    BASE_RE: str
    HEADER_RE: Pattern[str]
    BLOCK_RE: Pattern[str]
    INLINE_RE: Pattern[str]
    NAME_RE: Pattern[str]
    def assign_attrs(self, elem, attrs) -> None: ...
    def sanitize_name(self, name): ...

class AttrListExtension(Extension): ...

def makeExtension(**kwargs): ...
