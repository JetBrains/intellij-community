#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any, TypeVar

_FT = TypeVar("_FT")

def register(func: _FT, *args: Any, **kargs: Any) -> _FT: ...
