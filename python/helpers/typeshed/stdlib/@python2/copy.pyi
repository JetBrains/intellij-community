#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any, TypeVar

_T = TypeVar("_T")

# None in CPython but non-None in Jython
PyStringMap: Any

# Note: memo and _nil are internal kwargs.
def deepcopy(x: _T, memo: dict[int, Any] | None = ..., _nil: Any = ...) -> _T: ...
def copy(x: _T) -> _T: ...

class Error(Exception): ...

error = Error
