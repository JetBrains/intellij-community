import re

from . import base

spaceCharacters: str
SPACES_REGEX: re.Pattern[str]

class Filter(base.Filter):
    spacePreserveElements: frozenset[str]
    def __iter__(self): ...

def collapse_spaces(text: str) -> str: ...
