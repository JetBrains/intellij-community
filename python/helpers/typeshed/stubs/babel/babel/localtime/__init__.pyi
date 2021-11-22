from datetime import tzinfo
from typing import Any

STDOFFSET: Any
DSTOFFSET: Any
DSTDIFF: Any
ZERO: Any

class _FallbackLocalTimezone(tzinfo):
    def utcoffset(self, dt): ...
    def dst(self, dt): ...
    def tzname(self, dt): ...

def get_localzone(): ...

LOCALTZ: Any
