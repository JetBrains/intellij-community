from typing import Any, Pattern

from markdown.extensions import Extension
from markdown.inlinepatterns import InlineProcessor
from markdown.preprocessors import Preprocessor

ABBR_REF_RE: Pattern

class AbbrExtension(Extension):
    def extendMarkdown(self, md) -> None: ...

class AbbrPreprocessor(Preprocessor):
    def run(self, lines): ...

class AbbrInlineProcessor(InlineProcessor):
    title: Any
    def __init__(self, pattern, title) -> None: ...
    def handleMatch(self, m, data): ...  # type: ignore

def makeExtension(**kwargs): ...
