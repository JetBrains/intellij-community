from typing import Any

from ..utils.compat import string_types as string_types

log: Any

class Throwable:
    id: Any
    message: Any
    type: Any
    remote: Any
    stack: Any
    def __init__(self, exception, stack, remote: bool = ...) -> None: ...
    def to_dict(self): ...
