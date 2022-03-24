from typing import Any

# Enable when pyasn1 gets stubs:
# from pyasn1.type.univ import Integer, OctetString, Sequence
Integer = Any
OctetString = Any
Sequence = Any

MAXINT: Any
rangeInt0ToMaxConstraint: Any

class Integer0ToMax(Integer):
    subtypeSpec: Any

class Size(Integer0ToMax): ...
class Cookie(OctetString): ...

class RealSearchControlValue(Sequence):
    componentType: Any

def paged_search_control(criticality: bool = ..., size: int = ..., cookie: Any | None = ...): ...
