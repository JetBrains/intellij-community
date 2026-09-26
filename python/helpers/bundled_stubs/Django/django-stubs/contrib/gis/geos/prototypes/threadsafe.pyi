import threading
from typing import Any

from django.contrib.gis.geos.base import GEOSBase

class GEOSContextHandle(GEOSBase):
    ptr_type: Any
    destructor: Any
    ptr: Any
    def __init__(self) -> None: ...

class GEOSContext(threading.local):
    handle: Any

thread_context: Any

class GEOSFunc:
    cfunc: Any
    thread_context: Any
    def __init__(self, func_name: Any) -> None: ...
    def __call__(self, *args: Any) -> Any: ...
    argtypes: Any
    restype: Any
    errcheck: Any
