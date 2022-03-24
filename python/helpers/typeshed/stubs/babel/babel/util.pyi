import collections
import textwrap
from datetime import tzinfo
from typing import Any

from babel import localtime as localtime
from pytz import BaseTzInfo

missing: Any

def distinct(iterable) -> None: ...

PYTHON_MAGIC_COMMENT_re: Any

def parse_encoding(fp): ...

PYTHON_FUTURE_IMPORT_re: Any

def parse_future_flags(fp, encoding: str = ...): ...
def pathmatch(pattern, filename): ...

class TextWrapper(textwrap.TextWrapper):
    wordsep_re: Any

def wraptext(text, width: int = ..., initial_indent: str = ..., subsequent_indent: str = ...): ...

odict = collections.OrderedDict

class FixedOffsetTimezone(tzinfo):
    zone: Any
    def __init__(self, offset, name: Any | None = ...) -> None: ...
    def utcoffset(self, dt): ...
    def tzname(self, dt): ...
    def dst(self, dt): ...

UTC: BaseTzInfo
LOCALTZ: BaseTzInfo
get_localzone = localtime.get_localzone
STDOFFSET: Any
DSTOFFSET: Any
DSTDIFF: Any
ZERO: Any
