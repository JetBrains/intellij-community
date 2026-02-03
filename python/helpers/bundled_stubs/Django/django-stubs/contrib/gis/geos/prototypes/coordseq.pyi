from typing import Any

from django.contrib.gis.geos.libgeos import GEOSFuncFactory

def check_cs_op(result: Any, func: Any, cargs: Any) -> Any: ...
def check_cs_get(result: Any, func: Any, cargs: Any) -> Any: ...

class CsInt(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

class CsOperation(GEOSFuncFactory):
    restype: Any
    def __init__(self, *args: Any, ordinate: bool = ..., get: bool = ..., **kwargs: Any) -> None: ...

class CsOutput(GEOSFuncFactory):
    restype: Any
    @staticmethod
    def errcheck(result: Any, func: Any, cargs: Any) -> Any: ...

cs_clone: Any
create_cs: Any
get_cs: Any
cs_getordinate: Any
cs_setordinate: Any
cs_getx: Any
cs_gety: Any
cs_getz: Any
cs_setx: Any
cs_sety: Any
cs_setz: Any
cs_getsize: Any
cs_getdims: Any
cs_is_ccw: Any
