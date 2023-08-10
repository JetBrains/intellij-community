from re import Pattern
from typing import Any

from markdown.blockprocessors import BlockProcessor
from markdown.extensions import Extension
from markdown.inlinepatterns import InlineProcessor

ABBR_REF_RE: Pattern[str]

class AbbrExtension(Extension): ...
class AbbrPreprocessor(BlockProcessor): ...

class AbbrInlineProcessor(InlineProcessor):
    title: Any
    def __init__(self, pattern, title) -> None: ...

def makeExtension(**kwargs): ...
