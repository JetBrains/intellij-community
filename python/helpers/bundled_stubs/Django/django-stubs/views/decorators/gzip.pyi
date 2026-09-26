from collections.abc import Callable
from typing import Any

from typing_extensions import TypeVar

_C = TypeVar("_C", bound=Callable[..., Any])

gzip_page: Callable[[_C], _C]
