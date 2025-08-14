from typing import Any

from django.db.models import Lookup, Transform

class RasterBandTransform(Transform): ...

class GISLookup(Lookup):
    sql_template: Any
    transform_func: Any
    distance: bool
    band_rhs: Any
    band_lhs: Any
    template_params: Any
    def __init__(self, lhs: Any, rhs: Any) -> None: ...
    def process_rhs_params(self) -> None: ...
    def process_band_indices(self, only_lhs: bool = ...) -> None: ...
    def get_db_prep_lookup(self, value: Any, connection: Any) -> Any: ...
    rhs: Any
    def process_rhs(self, compiler: Any, connection: Any) -> Any: ...
    def get_rhs_op(self, connection: Any, rhs: Any) -> Any: ...

class OverlapsLeftLookup(GISLookup):
    lookup_name: str

class OverlapsRightLookup(GISLookup):
    lookup_name: str

class OverlapsBelowLookup(GISLookup):
    lookup_name: str

class OverlapsAboveLookup(GISLookup):
    lookup_name: str

class LeftLookup(GISLookup):
    lookup_name: str

class RightLookup(GISLookup):
    lookup_name: str

class StrictlyBelowLookup(GISLookup):
    lookup_name: str

class StrictlyAboveLookup(GISLookup):
    lookup_name: str

class SameAsLookup(GISLookup):
    lookup_name: str

class BBContainsLookup(GISLookup):
    lookup_name: str

class BBOverlapsLookup(GISLookup):
    lookup_name: str

class ContainedLookup(GISLookup):
    lookup_name: str

class ContainsLookup(GISLookup):
    lookup_name: str

class ContainsProperlyLookup(GISLookup):
    lookup_name: str

class CoveredByLookup(GISLookup):
    lookup_name: str

class CoversLookup(GISLookup):
    lookup_name: str

class CrossesLookup(GISLookup):
    lookup_name: str

class DisjointLookup(GISLookup):
    lookup_name: str

class EqualsLookup(GISLookup):
    lookup_name: str

class IntersectsLookup(GISLookup):
    lookup_name: str

class OverlapsLookup(GISLookup):
    lookup_name: str

class RelateLookup(GISLookup):
    lookup_name: str
    sql_template: str
    pattern_regex: Any
    def process_rhs(self, compiler: Any, connection: Any) -> Any: ...

class TouchesLookup(GISLookup):
    lookup_name: str

class WithinLookup(GISLookup):
    lookup_name: str

class DistanceLookupBase(GISLookup):
    distance: bool
    sql_template: str
    def process_rhs_params(self) -> None: ...
    def process_distance(self, compiler: Any, connection: Any) -> Any: ...

class DWithinLookup(DistanceLookupBase):
    lookup_name: str
    sql_template: str
    def process_distance(self, compiler: Any, connection: Any) -> Any: ...
    def process_rhs(self, compiler: Any, connection: Any) -> Any: ...

class DistanceLookupFromFunction(DistanceLookupBase): ...

class DistanceGTLookup(DistanceLookupFromFunction):
    lookup_name: str
    op: str

class DistanceGTELookup(DistanceLookupFromFunction):
    lookup_name: str
    op: str

class DistanceLTLookup(DistanceLookupFromFunction):
    lookup_name: str
    op: str

class DistanceLTELookup(DistanceLookupFromFunction):
    lookup_name: str
    op: str
