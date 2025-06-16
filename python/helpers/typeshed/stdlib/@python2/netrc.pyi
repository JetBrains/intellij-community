#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Text

class NetrcParseError(Exception):
    filename: str | None
    lineno: int | None
    msg: str
    def __init__(self, msg: str, filename: Text | None = ..., lineno: int | None = ...) -> None: ...

# (login, account, password) tuple
_NetrcTuple = tuple[str, str | None, str | None]

class netrc:
    hosts: dict[str, _NetrcTuple]
    macros: dict[str, list[str]]
    def __init__(self, file: Text | None = ...) -> None: ...
    def authenticators(self, host: str) -> _NetrcTuple | None: ...
