from ctypes import Structure
from logging import Logger
from typing import Any

from django.utils.functional import cached_property

logger: Logger

def load_geos() -> Any: ...

NOTICEFUNC: Any
notice_h: Any

ERRORFUNC: Any
error_h: Any

class GEOSGeom_t(Structure): ...
class GEOSPrepGeom_t(Structure): ...
class GEOSCoordSeq_t(Structure): ...
class GEOSContextHandle_t(Structure): ...

GEOM_PTR: Any
PREPGEOM_PTR: Any
CS_PTR: Any
CONTEXT_PTR: Any
lgeos: Any

class GEOSFuncFactory:
    argtypes: Any
    restype: Any
    errcheck: Any
    func_name: Any
    def __init__(
        self, func_name: Any, *, restype: Any | None = ..., errcheck: Any | None = ..., argtypes: Any | None = ...
    ) -> None: ...
    def __call__(self, *args: Any) -> Any: ...
    @cached_property
    def func(self) -> Any: ...

def geos_version() -> bytes: ...
def geos_version_tuple() -> tuple[int, ...]: ...
