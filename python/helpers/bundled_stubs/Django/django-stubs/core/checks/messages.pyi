from typing import Any

DEBUG: int
INFO: int
WARNING: int
ERROR: int
CRITICAL: int

class CheckMessage:
    level: int
    msg: str
    hint: str | None
    obj: Any
    id: str | None
    def __init__(
        self, level: int, msg: str, hint: str | None = None, obj: Any | None = None, id: str | None = None
    ) -> None: ...
    def is_serious(self, level: int = 40) -> bool: ...
    def is_silenced(self) -> bool: ...

class Debug(CheckMessage):
    def __init__(self, msg: str, hint: str | None = ..., obj: Any = ..., id: str | None = ...) -> None: ...

class Info(CheckMessage):
    def __init__(self, msg: str, hint: str | None = ..., obj: Any = ..., id: str | None = ...) -> None: ...

class Warning(CheckMessage):
    def __init__(self, msg: str, hint: str | None = ..., obj: Any = ..., id: str | None = ...) -> None: ...

class Error(CheckMessage):
    def __init__(self, msg: str, hint: str | None = ..., obj: Any = ..., id: str | None = ...) -> None: ...

class Critical(CheckMessage):
    def __init__(self, msg: str, hint: str | None = ..., obj: Any = ..., id: str | None = ...) -> None: ...
