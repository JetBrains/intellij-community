#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Mapping, Sequence

_Cap = dict[str, str | int]

def findmatch(
    caps: Mapping[str, list[_Cap]], MIMEtype: str, key: str = ..., filename: str = ..., plist: Sequence[str] = ...
) -> tuple[str | None, _Cap | None]: ...
def getcaps() -> dict[str, list[_Cap]]: ...
