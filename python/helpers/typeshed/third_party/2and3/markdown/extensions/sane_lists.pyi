from typing import Any, Pattern

from markdown.blockprocessors import OListProcessor, UListProcessor
from markdown.extensions import Extension

class SaneOListProcessor(OListProcessor):
    SIBLING_TAGS: Any
    LAZY_OL: bool = ...
    CHILD_RE: Pattern
    def __init__(self, parser) -> None: ...

class SaneUListProcessor(UListProcessor):
    SIBLING_TAGS: Any
    CHILD_RE: Pattern
    def __init__(self, parser) -> None: ...

class SaneListExtension(Extension):
    def extendMarkdown(self, md) -> None: ...

def makeExtension(**kwargs): ...
