from django.contrib.gis.geos.geometry import GEOSGeometry
from django.contrib.gis.geos.prototypes.io import WKBWriter as WKBWriter
from django.contrib.gis.geos.prototypes.io import WKTWriter as WKTWriter
from django.contrib.gis.geos.prototypes.io import _WKBReader, _WKTReader

class WKBReader(_WKBReader):
    def read(self, wkb: bytes | str) -> GEOSGeometry: ...

class WKTReader(_WKTReader):
    def read(self, wkt: bytes | str) -> GEOSGeometry: ...
