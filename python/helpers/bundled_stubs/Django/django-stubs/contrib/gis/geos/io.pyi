from django.contrib.gis.geos.geometry import GEOSGeometry
from django.contrib.gis.geos.prototypes.io import WKBWriter as WKBWriter
from django.contrib.gis.geos.prototypes.io import WKTWriter as WKTWriter
from django.contrib.gis.geos.prototypes.io import _WKBReader, _WKTReader
from typing_extensions import override

class WKBReader(_WKBReader):
    @override
    def read(self, wkb: bytes | str) -> GEOSGeometry: ...

class WKTReader(_WKTReader):
    @override
    def read(self, wkt: bytes | str) -> GEOSGeometry: ...

__all__ = ["WKBReader", "WKBWriter", "WKTReader", "WKTWriter"]
