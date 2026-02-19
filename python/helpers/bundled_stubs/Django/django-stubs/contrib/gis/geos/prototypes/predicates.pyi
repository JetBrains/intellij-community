from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

class UnaryPredicate(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

class BinaryPredicate(UnaryPredicate):
    argtypes: Any

geos_hasz: UnaryPredicate
geos_isclosed: UnaryPredicate
geos_isempty: UnaryPredicate
geos_isring: UnaryPredicate
geos_issimple: UnaryPredicate
geos_isvalid: UnaryPredicate
geos_contains: BinaryPredicate
geos_covers: BinaryPredicate
geos_crosses: BinaryPredicate
geos_disjoint: BinaryPredicate
geos_equals: BinaryPredicate
geos_equalsexact: BinaryPredicate
geos_equalsidentical: BinaryPredicate
geos_intersects: BinaryPredicate
geos_overlaps: BinaryPredicate
geos_relatepattern: BinaryPredicate
geos_touches: BinaryPredicate
geos_within: BinaryPredicate
