from collections.abc import Callable
from typing import TypeAlias, TypeVar

from Xlib.error import XError
from Xlib.protocol.rq import Request

_T = TypeVar("_T")
ErrorHandler: TypeAlias = Callable[[XError, Request | None], _T]
