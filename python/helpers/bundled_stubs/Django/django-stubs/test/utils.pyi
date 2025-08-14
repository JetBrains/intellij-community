import decimal
from collections.abc import Callable, Iterable, Iterator, Mapping
from contextlib import AbstractContextManager, contextmanager
from decimal import Decimal
from io import StringIO
from logging import Logger
from types import TracebackType
from typing import Any, Protocol, SupportsIndex, TypeVar, type_check_only

from django.apps.registry import Apps
from django.conf import LazySettings, Settings
from django.core.checks.registry import CheckRegistry
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.lookups import Lookup, Transform
from django.db.models.query_utils import RegisterLookupMixin
from django.test.runner import DiscoverRunner
from django.test.testcases import SimpleTestCase
from typing_extensions import Self, TypeAlias

_TestClass: TypeAlias = type[SimpleTestCase]

_DecoratedTest: TypeAlias = Callable | _TestClass
_DT = TypeVar("_DT", bound=_DecoratedTest)
_C = TypeVar("_C", bound=Callable)  # Any callable

TZ_SUPPORT: bool

class Approximate:
    val: decimal.Decimal | float
    places: int
    def __init__(self, val: Decimal | float, places: int = ...) -> None: ...

class ContextList(list[dict[str, Any]]):
    def __getitem__(self, key: str | SupportsIndex | slice) -> Any: ...
    def get(self, key: str, default: Any | None = ...) -> Any: ...
    def __contains__(self, key: object) -> bool: ...
    def keys(self) -> set[str]: ...

class _TestState: ...

def setup_test_environment(debug: bool | None = ...) -> None: ...
def teardown_test_environment() -> None: ...
def get_runner(settings: LazySettings, test_runner_class: str | None = ...) -> type[DiscoverRunner]: ...

class TestContextDecorator:
    attr_name: str | None
    kwarg_name: str | None
    def __init__(self, attr_name: str | None = ..., kwarg_name: str | None = ...) -> None: ...
    def enable(self) -> Any: ...
    def disable(self) -> None: ...
    def __enter__(self) -> Apps | None: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...
    def decorate_class(self, cls: _TestClass) -> _TestClass: ...
    def decorate_callable(self, func: _C) -> _C: ...
    def __call__(self, decorated: _DT) -> _DT: ...

class override_settings(TestContextDecorator):
    enable_exception: Exception | None
    options: dict[str, Any]
    def __init__(self, **kwargs: Any) -> None: ...
    wrapped: Settings
    def save_options(self, test_func: _DecoratedTest) -> None: ...
    def decorate_class(self, cls: type) -> type: ...

class modify_settings(override_settings):
    wrapped: Settings
    operations: list[tuple[str, dict[str, list[str] | str]]]
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def save_options(self, test_func: _DecoratedTest) -> None: ...
    options: dict[str, list[tuple[str, str] | str]]

class override_system_checks(TestContextDecorator):
    registry: CheckRegistry
    new_checks: list[Callable]
    deployment_checks: list[Callable] | None
    def __init__(self, new_checks: list[Callable], deployment_checks: list[Callable] | None = ...) -> None: ...
    old_checks: set[Callable]
    old_deployment_checks: set[Callable]

class CaptureQueriesContext:
    connection: BaseDatabaseWrapper
    force_debug_cursor: bool
    initial_queries: int
    final_queries: int | None
    def __init__(self, connection: BaseDatabaseWrapper) -> None: ...
    def __iter__(self) -> Iterator[dict[str, str]]: ...
    def __getitem__(self, index: int) -> dict[str, str]: ...
    def __len__(self) -> int: ...
    @property
    def captured_queries(self) -> list[dict[str, str]]: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...

class ignore_warnings(TestContextDecorator):
    ignore_kwargs: dict[str, Any]
    filter_func: Callable
    def __init__(self, **kwargs: Any) -> None: ...
    catch_warnings: AbstractContextManager[list | None]

requires_tz_support: Any

@contextmanager
def isolate_lru_cache(lru_cache_object: Callable) -> Iterator[None]: ...

class override_script_prefix(TestContextDecorator):
    prefix: str
    def __init__(self, prefix: str) -> None: ...
    old_prefix: str

class LoggingCaptureMixin:
    logger: Logger
    old_stream: Any
    logger_output: Any
    def setUp(self) -> None: ...
    def tearDown(self) -> None: ...

class isolate_apps(TestContextDecorator):
    installed_apps: tuple[str, ...]
    def __init__(self, *installed_apps: Any, **kwargs: Any) -> None: ...
    old_apps: Apps

@contextmanager
def extend_sys_path(*paths: str) -> Iterator[None]: ...
@contextmanager
def captured_output(stream_name: str) -> Iterator[StringIO]: ...
def captured_stdin() -> AbstractContextManager: ...
def captured_stdout() -> AbstractContextManager: ...
def captured_stderr() -> AbstractContextManager: ...
@contextmanager
def freeze_time(t: float) -> Iterator[None]: ...
def tag(*tags: str) -> Callable[[_C], _C]: ...

_Signature: TypeAlias = str
_TestDatabase: TypeAlias = tuple[str, list[str]]

@type_check_only
class TimeKeeperProtocol(Protocol):
    @contextmanager
    def timed(self, name: Any) -> Iterator[None]: ...
    def print_results(self) -> None: ...

def dependency_ordered(
    test_databases: Iterable[tuple[_Signature, _TestDatabase]], dependencies: Mapping[str, list[str]]
) -> list[tuple[_Signature, _TestDatabase]]: ...
def get_unique_databases_and_mirrors(
    aliases: set[str] | None = ...,
) -> tuple[dict[_Signature, _TestDatabase], dict[str, Any]]: ...
def setup_databases(
    verbosity: int,
    interactive: bool,
    *,
    time_keeper: TimeKeeperProtocol | None = ...,
    keepdb: bool = ...,
    debug_sql: bool = ...,
    parallel: int = ...,
    aliases: Mapping[str, Any] | None = ...,
    serialized_aliases: Iterable[str] | None = ...,
    **kwargs: Any,
) -> list[tuple[BaseDatabaseWrapper, str, bool]]: ...
def teardown_databases(
    old_config: Iterable[tuple[Any, str, bool]], verbosity: int, parallel: int = ..., keepdb: bool = ...
) -> None: ...
def require_jinja2(test_func: _C) -> _C: ...
@contextmanager
def register_lookup(
    field: type[RegisterLookupMixin], *lookups: type[Lookup | Transform], lookup_name: str | None = ...
) -> Iterator[None]: ...
