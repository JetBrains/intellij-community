from re import Pattern

from markdown.extensions import Extension
from markdown.treeprocessors import Treeprocessor

ATTR_RE: Pattern[str]

class LegacyAttrs(Treeprocessor):
    def handleAttributes(self, el, txt): ...

class LegacyAttrExtension(Extension): ...

def makeExtension(**kwargs): ...
