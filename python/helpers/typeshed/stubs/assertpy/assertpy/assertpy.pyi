import logging
from collections.abc import Callable, Generator
from typing import Any
from typing_extensions import Self

from .base import BaseMixin
from .collection import CollectionMixin
from .contains import ContainsMixin
from .date import DateMixin
from .dict import DictMixin
from .dynamic import DynamicMixin
from .exception import ExceptionMixin
from .extracting import ExtractingMixin
from .file import FileMixin
from .helpers import HelpersMixin
from .numeric import NumericMixin
from .snapshot import SnapshotMixin
from .string import StringMixin

__version__: str
__tracebackhide__: bool

class WarningLoggingAdapter(logging.LoggerAdapter[logging.Logger]):
    def process(self, msg: str, kwargs: Any) -> tuple[str, Any]: ...

class AssertionBuilder(
    StringMixin,
    SnapshotMixin,
    NumericMixin,
    HelpersMixin,
    FileMixin,
    ExtractingMixin,
    ExceptionMixin,
    DynamicMixin,
    DictMixin,
    DateMixin,
    ContainsMixin,
    CollectionMixin,
    BaseMixin,
):
    val: Any
    description: str
    kind: str | None
    expected: BaseException | None
    logger: logging.Logger
    def __init__(
        self,
        val: Any,
        description: str = "",
        kind: str | None = None,
        expected: BaseException | None = None,
        logger: logging.Logger | None = None,
    ) -> None: ...
    def builder(
        self,
        val: Any,
        description: str = "",
        kind: str | None = None,
        expected: BaseException | None = None,
        logger: logging.Logger | None = None,
    ) -> Self: ...
    def error(self, msg: str) -> Self: ...

def soft_assertions() -> Generator[None, None, None]: ...
def assert_that(val: Any, description: str = "") -> AssertionBuilder: ...
def assert_warn(val: Any, description: str = "", logger: logging.Logger | None = None) -> AssertionBuilder: ...
def fail(msg: str = "") -> None: ...
def soft_fail(msg: str = "") -> None: ...
def add_extension(func: Callable[[AssertionBuilder], AssertionBuilder]) -> None: ...
def remove_extension(func: Callable[[AssertionBuilder], AssertionBuilder]) -> None: ...
