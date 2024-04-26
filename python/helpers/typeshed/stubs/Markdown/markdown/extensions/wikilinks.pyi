from typing import Any

from markdown.core import Markdown
from markdown.extensions import Extension
from markdown.inlinepatterns import InlineProcessor

def build_url(label, base, end): ...

class WikiLinkExtension(Extension):
    def __init__(self, **kwargs) -> None: ...
    md: Markdown

class WikiLinksInlineProcessor(InlineProcessor):
    config: Any
    def __init__(self, pattern, config) -> None: ...

def makeExtension(**kwargs): ...
