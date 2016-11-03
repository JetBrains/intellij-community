from typing import Any, List

default_action = ...  # type: str
filters = ...  # type: List[tuple]
once_registry = ...  # type: dict

def warn(message: Warning, category:type = ..., stacklevel:int = ...) -> None: ...
def warn_explicit(message: Warning, category:type,
                  filename: str, lineno: int,
                  module:Any = ..., registry:dict = ...,
                  module_globals:dict = ...) -> None: ...
