from typing import Any, Protocol, type_check_only

from django.contrib.gis.geos.geometry import GEOSGeometry

@type_check_only
class _Reader(Protocol):
    def read(self) -> str | bytes: ...

def fromfile(file_h: str | _Reader) -> GEOSGeometry: ...
def fromstr(string: str, **kwargs: Any) -> GEOSGeometry: ...
