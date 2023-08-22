from re import Pattern
from typing import Any

from markdown.blockprocessors import BlockProcessor
from markdown.extensions import Extension

class AdmonitionExtension(Extension): ...

class AdmonitionProcessor(BlockProcessor):
    CLASSNAME: str
    CLASSNAME_TITLE: str
    RE: Pattern[str]
    RE_SPACES: Any
    def get_class_and_title(self, match): ...

def makeExtension(**kwargs): ...
