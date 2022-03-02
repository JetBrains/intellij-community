import sys
from _typeshed import Self
from typing import Any, Awaitable, Callable, Generic, Iterable, Mapping, Sequence, TypeVar, overload
from typing_extensions import Literal

_T = TypeVar("_T")
_TT = TypeVar("_TT", bound=type[Any])
_R = TypeVar("_R")

if sys.version_info >= (3, 8):
    __all__ = (
        "Mock",
        "MagicMock",
        "patch",
        "sentinel",
        "DEFAULT",
        "ANY",
        "call",
        "create_autospec",
        "AsyncMock",
        "FILTER_DIR",
        "NonCallableMock",
        "NonCallableMagicMock",
        "mock_open",
        "PropertyMock",
        "seal",
    )
elif sys.version_info >= (3, 7):
    __all__ = (
        "Mock",
        "MagicMock",
        "patch",
        "sentinel",
        "DEFAULT",
        "ANY",
        "call",
        "create_autospec",
        "FILTER_DIR",
        "NonCallableMock",
        "NonCallableMagicMock",
        "mock_open",
        "PropertyMock",
        "seal",
    )
else:
    __all__ = (
        "Mock",
        "MagicMock",
        "patch",
        "sentinel",
        "DEFAULT",
        "ANY",
        "call",
        "create_autospec",
        "FILTER_DIR",
        "NonCallableMock",
        "NonCallableMagicMock",
        "mock_open",
        "PropertyMock",
    )
__version__: str

FILTER_DIR: Any

class _slotted: ...

class _SentinelObject:
    name: Any
    def __init__(self, name: Any) -> None: ...

class _Sentinel:
    def __init__(self) -> None: ...
    def __getattr__(self, name: str) -> Any: ...

sentinel: Any
DEFAULT: Any

class _Call(tuple[Any, ...]):
    def __new__(
        cls: type[Self],
        value: Any = ...,
        name: Any | None = ...,
        parent: Any | None = ...,
        two: bool = ...,
        from_kall: bool = ...,
    ) -> Self: ...
    name: Any
    parent: Any
    from_kall: Any
    def __init__(
        self, value: Any = ..., name: Any | None = ..., parent: Any | None = ..., two: bool = ..., from_kall: bool = ...
    ) -> None: ...
    def __eq__(self, other: object) -> bool: ...
    def __ne__(self, other: object) -> bool: ...
    def __call__(self, *args: Any, **kwargs: Any) -> _Call: ...
    def __getattr__(self, attr: Any) -> Any: ...
    if sys.version_info >= (3, 8):
        @property
        def args(self): ...
        @property
        def kwargs(self): ...

    def call_list(self) -> Any: ...

call: _Call

class _CallList(list[_Call]):
    def __contains__(self, value: Any) -> bool: ...

class Base:
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...

class NonCallableMock(Base, Any):
    def __new__(__cls: type[Self], *args: Any, **kw: Any) -> Self: ...
    def __init__(
        self,
        spec: list[str] | object | type[object] | None = ...,
        wraps: Any | None = ...,
        name: str | None = ...,
        spec_set: list[str] | object | type[object] | None = ...,
        parent: NonCallableMock | None = ...,
        _spec_state: Any | None = ...,
        _new_name: str = ...,
        _new_parent: NonCallableMock | None = ...,
        _spec_as_instance: bool = ...,
        _eat_self: bool | None = ...,
        unsafe: bool = ...,
        **kwargs: Any,
    ) -> None: ...
    def __getattr__(self, name: str) -> Any: ...
    if sys.version_info >= (3, 8):
        def _calls_repr(self, prefix: str = ...) -> str: ...
        def assert_called_with(self, *args: Any, **kwargs: Any) -> None: ...
        def assert_not_called(self) -> None: ...
        def assert_called_once_with(self, *args: Any, **kwargs: Any) -> None: ...
        def _format_mock_failure_message(self, args: Any, kwargs: Any, action: str = ...) -> str: ...
    else:
        def assert_called_with(_mock_self, *args: Any, **kwargs: Any) -> None: ...
        def assert_not_called(_mock_self) -> None: ...
        def assert_called_once_with(_mock_self, *args: Any, **kwargs: Any) -> None: ...
        def _format_mock_failure_message(self, args: Any, kwargs: Any) -> str: ...
    if sys.version_info >= (3, 8):
        def assert_called(self) -> None: ...
        def assert_called_once(self) -> None: ...
    else:
        def assert_called(_mock_self) -> None: ...
        def assert_called_once(_mock_self) -> None: ...

    def reset_mock(self, visited: Any = ..., *, return_value: bool = ..., side_effect: bool = ...) -> None: ...
    if sys.version_info >= (3, 7):
        def _extract_mock_name(self) -> str: ...
        def _get_call_signature_from_name(self, name: str) -> Any: ...

    def assert_any_call(self, *args: Any, **kwargs: Any) -> None: ...
    def assert_has_calls(self, calls: Sequence[_Call], any_order: bool = ...) -> None: ...
    def mock_add_spec(self, spec: Any, spec_set: bool = ...) -> None: ...
    def _mock_add_spec(self, spec: Any, spec_set: bool, _spec_as_instance: bool = ..., _eat_self: bool = ...) -> None: ...
    def attach_mock(self, mock: NonCallableMock, attribute: str) -> None: ...
    def configure_mock(self, **kwargs: Any) -> None: ...
    return_value: Any
    side_effect: Any
    called: bool
    call_count: int
    call_args: Any
    call_args_list: _CallList
    mock_calls: _CallList
    def _format_mock_call_signature(self, args: Any, kwargs: Any) -> str: ...
    def _call_matcher(self, _call: tuple[_Call, ...]) -> _Call: ...
    def _get_child_mock(self, **kw: Any) -> NonCallableMock: ...

class CallableMixin(Base):
    side_effect: Any
    def __init__(
        self,
        spec: Any | None = ...,
        side_effect: Any | None = ...,
        return_value: Any = ...,
        wraps: Any | None = ...,
        name: Any | None = ...,
        spec_set: Any | None = ...,
        parent: Any | None = ...,
        _spec_state: Any | None = ...,
        _new_name: Any = ...,
        _new_parent: Any | None = ...,
        **kwargs: Any,
    ) -> None: ...
    if sys.version_info >= (3, 8):
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
    else:
        def __call__(_mock_self, *args: Any, **kwargs: Any) -> Any: ...

class Mock(CallableMixin, NonCallableMock): ...

class _patch(Generic[_T]):
    attribute_name: Any
    getter: Callable[[], Any]
    attribute: str
    new: _T
    new_callable: Any
    spec: Any
    create: bool
    has_local: Any
    spec_set: Any
    autospec: Any
    kwargs: Mapping[str, Any]
    additional_patchers: Any
    # If new==DEFAULT, self is _patch[Any]. Ideally we'd be able to add an overload for it so that self is _patch[MagicMock],
    # but that's impossible with the current type system.
    if sys.version_info >= (3, 10):
        def __init__(
            self: _patch[_T],
            getter: Callable[[], Any],
            attribute: str,
            new: _T,
            spec: Any | None,
            create: bool,
            spec_set: Any | None,
            autospec: Any | None,
            new_callable: Any | None,
            kwargs: Mapping[str, Any],
            *,
            unsafe: bool = ...,
        ) -> None: ...
    else:
        def __init__(
            self: _patch[_T],
            getter: Callable[[], Any],
            attribute: str,
            new: _T,
            spec: Any | None,
            create: bool,
            spec_set: Any | None,
            autospec: Any | None,
            new_callable: Any | None,
            kwargs: Mapping[str, Any],
        ) -> None: ...

    def copy(self) -> _patch[_T]: ...
    @overload
    def __call__(self, func: _TT) -> _TT: ...
    @overload
    def __call__(self, func: Callable[..., _R]) -> Callable[..., _R]: ...
    if sys.version_info >= (3, 8):
        def decoration_helper(self, patched, args, keywargs): ...

    def decorate_class(self, klass: _TT) -> _TT: ...
    def decorate_callable(self, func: Callable[..., _R]) -> Callable[..., _R]: ...
    if sys.version_info >= (3, 8):
        def decorate_async_callable(self, func: Callable[..., Awaitable[_R]]) -> Callable[..., Awaitable[_R]]: ...

    def get_original(self) -> tuple[Any, bool]: ...
    target: Any
    temp_original: Any
    is_local: bool
    def __enter__(self) -> _T: ...
    def __exit__(self, *exc_info: Any) -> None: ...
    def start(self) -> _T: ...
    def stop(self) -> None: ...

class _patch_dict:
    in_dict: Any
    values: Any
    clear: Any
    def __init__(self, in_dict: Any, values: Any = ..., clear: Any = ..., **kwargs: Any) -> None: ...
    def __call__(self, f: Any) -> Any: ...
    def decorate_class(self, klass: Any) -> Any: ...
    def __enter__(self) -> Any: ...
    def __exit__(self, *args: Any) -> Any: ...
    start: Any
    stop: Any

class _patcher:
    TEST_PREFIX: str
    dict: type[_patch_dict]
    if sys.version_info >= (3, 8):
        # This overload also covers the case, where new==DEFAULT. In this case, the return type is _patch[Any].
        # Ideally we'd be able to add an overload for it so that the return type is _patch[MagicMock],
        # but that's impossible with the current type system.
        @overload
        def __call__(  # type: ignore[misc]
            self,
            target: Any,
            new: _T,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[_T]: ...
        @overload
        def __call__(
            self,
            target: Any,
            *,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[MagicMock | AsyncMock]: ...
    else:
        @overload
        def __call__(  # type: ignore[misc]
            self,
            target: Any,
            new: _T,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[_T]: ...
        @overload
        def __call__(
            self,
            target: Any,
            *,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[MagicMock]: ...
    if sys.version_info >= (3, 8):
        @overload
        def object(  # type: ignore[misc]
            self,
            target: Any,
            attribute: str,
            new: _T,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[_T]: ...
        @overload
        def object(
            self,
            target: Any,
            attribute: str,
            *,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[MagicMock | AsyncMock]: ...
    else:
        @overload
        def object(  # type: ignore[misc]
            self,
            target: Any,
            attribute: str,
            new: _T,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[_T]: ...
        @overload
        def object(
            self,
            target: Any,
            attribute: str,
            *,
            spec: Any | None = ...,
            create: bool = ...,
            spec_set: Any | None = ...,
            autospec: Any | None = ...,
            new_callable: Any | None = ...,
            **kwargs: Any,
        ) -> _patch[MagicMock]: ...

    def multiple(
        self,
        target: Any,
        spec: Any | None = ...,
        create: bool = ...,
        spec_set: Any | None = ...,
        autospec: Any | None = ...,
        new_callable: Any | None = ...,
        **kwargs: Any,
    ) -> _patch[Any]: ...
    def stopall(self) -> None: ...

patch: _patcher

class MagicMixin:
    def __init__(self, *args: Any, **kw: Any) -> None: ...

class NonCallableMagicMock(MagicMixin, NonCallableMock):
    def mock_add_spec(self, spec: Any, spec_set: bool = ...) -> None: ...

class MagicMock(MagicMixin, Mock):
    def mock_add_spec(self, spec: Any, spec_set: bool = ...) -> None: ...

if sys.version_info >= (3, 8):
    class AsyncMockMixin(Base):
        def __init__(self, *args: Any, **kwargs: Any) -> None: ...
        async def _execute_mock_call(self, *args: Any, **kwargs: Any) -> Any: ...
        def assert_awaited(self) -> None: ...
        def assert_awaited_once(self) -> None: ...
        def assert_awaited_with(self, *args: Any, **kwargs: Any) -> None: ...
        def assert_awaited_once_with(self, *args: Any, **kwargs: Any) -> None: ...
        def assert_any_await(self, *args: Any, **kwargs: Any) -> None: ...
        def assert_has_awaits(self, calls: Iterable[_Call], any_order: bool = ...) -> None: ...
        def assert_not_awaited(self) -> None: ...
        def reset_mock(self, *args: Any, **kwargs: Any) -> None: ...
        await_count: int
        await_args: _Call | None
        await_args_list: _CallList

    class AsyncMagicMixin(MagicMixin):
        def __init__(self, *args: Any, **kw: Any) -> None: ...

    class AsyncMock(AsyncMockMixin, AsyncMagicMixin, Mock): ...  # type: ignore # argument disparities between base classes

class MagicProxy:
    name: Any
    parent: Any
    def __init__(self, name, parent) -> None: ...
    if sys.version_info < (3, 8):
        def __call__(self, *args: Any, **kwargs: Any) -> Any: ...

    def create_mock(self): ...
    def __get__(self, obj, _type: Any | None = ...): ...

class _ANY:
    def __eq__(self, other: object) -> Literal[True]: ...
    def __ne__(self, other: object) -> Literal[False]: ...

ANY: Any

def create_autospec(
    spec: Any, spec_set: Any = ..., instance: Any = ..., _parent: Any | None = ..., _name: Any | None = ..., **kwargs: Any
) -> Any: ...

class _SpecState:
    spec: Any
    ids: Any
    spec_set: Any
    parent: Any
    instance: Any
    name: Any
    def __init__(
        self,
        spec: Any,
        spec_set: Any = ...,
        parent: Any | None = ...,
        name: Any | None = ...,
        ids: Any | None = ...,
        instance: Any = ...,
    ) -> None: ...

def mock_open(mock: Any | None = ..., read_data: Any = ...) -> Any: ...

PropertyMock = Any

if sys.version_info >= (3, 7):
    def seal(mock: Any) -> None: ...
