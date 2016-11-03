from typing import Any, List

_defaultaction = ...  # type: str
_onceregistry = ...  # type: dict
filters = ...  # type: List[tuple]

def warn(message: Warning, category:type = ..., stacklevel:int = ...) -> None: ...
def warn_explicit(message: Warning, category:type,
                  filename: str, lineno: int,
                  module:Any = ..., registry:dict = ...,
                  module_globals:dict = ...) -> None: ...
