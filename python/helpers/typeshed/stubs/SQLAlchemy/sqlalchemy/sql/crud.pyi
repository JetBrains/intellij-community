from typing import Any, Generic, TypeVar

from . import elements
from .operators import ColumnOperators

_T = TypeVar("_T")

REQUIRED: Any

class _multiparam_column(elements.ColumnElement[_T], Generic[_T]):
    index: Any
    key: Any
    original: Any
    default: Any
    type: Any
    def __init__(self, original, index) -> None: ...
    def compare(self, other, **kw) -> None: ...
    def __eq__(self, other) -> ColumnOperators[_T]: ...  # type: ignore[override]
