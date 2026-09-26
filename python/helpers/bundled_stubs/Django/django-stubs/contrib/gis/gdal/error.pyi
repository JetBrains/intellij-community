from typing import Any

class GDALException(Exception): ...
class SRSException(Exception): ...

OGRERR_DICT: Any
CPLERR_DICT: Any
ERR_NONE: int

def check_err(code: Any, cpl: bool = ...) -> None: ...
