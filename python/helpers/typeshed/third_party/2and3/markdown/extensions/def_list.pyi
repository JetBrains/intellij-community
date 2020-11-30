from typing import Any, Pattern

from markdown.blockprocessors import BlockProcessor, ListIndentProcessor
from markdown.extensions import Extension

class DefListProcessor(BlockProcessor):
    RE: Pattern
    NO_INDENT_RE: Pattern
    def test(self, parent, block): ...
    def run(self, parent, blocks): ...

class DefListIndentProcessor(ListIndentProcessor):
    ITEM_TYPES: Any
    LIST_TYPES: Any
    def create_item(self, parent, block) -> None: ...

class DefListExtension(Extension):
    def extendMarkdown(self, md) -> None: ...

def makeExtension(**kwargs): ...
