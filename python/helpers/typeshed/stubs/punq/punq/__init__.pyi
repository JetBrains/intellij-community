from _typeshed import Incomplete
from collections import defaultdict
from collections.abc import Callable
from enum import Enum, unique
from typing import Any, Final, Generic, NamedTuple, NewType, TypeVar, overload
from typing_extensions import Self, deprecated

_T = TypeVar("_T", default=Any)

__version__: Final[str]

@deprecated("Deprecated alias for `MissingDependencyError`.")
class MissingDependencyException(Exception): ...

class MissingDependencyError(MissingDependencyException): ...

@deprecated("Deprecated alias for `InvalidRegistrationError`.")
class InvalidRegistrationException(Exception): ...

class InvalidRegistrationError(InvalidRegistrationException): ...

class InvalidFactoryError(InvalidRegistrationError):
    def __init__(self, service, factory) -> None: ...

class InvalidSelfRegistrationError(InvalidRegistrationError):
    def __init__(self, service) -> None: ...

@deprecated("Deprecated alias for `InvalidForwardReferenceError`.")
class InvalidForwardReferenceException(Exception): ...

class InvalidForwardReferenceError(InvalidForwardReferenceException): ...

# TODO: Make this class Generic
class RegistrationScope:
    parent: RegistrationScope | None
    entries: defaultdict[Incomplete, list[Incomplete]]
    def __init__(self, parent: RegistrationScope | None = None) -> None: ...
    def child(self) -> Self: ...
    def append(self, key, value) -> None: ...
    def get(self, key) -> list[Incomplete]: ...

@unique
class Scope(Enum):
    transient = 0
    singleton = 1

class _Registration(NamedTuple, Generic[_T]):
    service: type[_T] | str
    scope: Scope
    builder: Callable[..., _T]
    needs: dict[str, Any]  # the type hints of the builder's parameters
    args: dict[str, Any]  # passed to builder at instantiation time
    cache: bool

_Empty = NewType("_Empty", object)  # a class at runtime
empty: Final[_Empty]

class _Registry:
    def __init__(self, parent: _Registry | None = None) -> None: ...
    def register_service_and_impl(
        self,
        service: type[_T] | str,
        scope: Scope,
        impl: type[_T],
        resolve_args: dict[str, Any],  # forwarded to _Registration.builder
        cache: bool = True,
    ) -> None: ...
    def register_service_and_instance(self, service: type[_T] | str, instance: _T) -> None: ...
    def register_concrete_service(
        self,
        service: type | str,
        scope: Scope,
        resolve_args: dict[str, Any] | None = None,  # forwarded to _Registration.builder
        cache: bool = True,
    ) -> None: ...
    def build_context(self, key: type | str, existing: _ResolutionContext | None = None) -> _ResolutionContext: ...
    def register(
        self,
        service: type[_T] | str,
        factory: Callable[..., _T] | _Empty = ...,
        instance: _T | _Empty = ...,
        scope: Scope = Scope.transient,
        cache: bool = True,
        **kwargs: Any,  # forwarded to _Registration.builder
    ) -> None: ...

class _ResolutionTarget(Generic[_T]):
    service: type[_T] | str
    impls: list[_Registration[_T]]
    def __init__(self, key: type[_T] | str, impls: list[_Registration[_T]]) -> None: ...
    def is_generic_list(self) -> bool: ...
    @property
    def generic_parameter(self) -> Any: ...  # returns the first annotated generic parameter of the service
    def next_impl(self) -> _Registration[_T] | None: ...

class _ResolutionContext:
    targets: dict[type | str, _ResolutionTarget[Any]]
    cache: dict[type | str, Any]  # resolved objects during this resolution
    service: type | str
    def __init__(self, key: type | str, impls: list[_Registration[Any]]) -> None: ...
    def target(self, key: type[_T] | str) -> _ResolutionTarget[_T]: ...
    def has_cached(self, key: type | str) -> bool: ...
    def __getitem__(self, key: type[_T] | str) -> _T: ...
    def __setitem__(self, key: type[_T] | str, value: _T) -> None: ...
    def all_registrations(self, service: type[_T] | str) -> list[_Registration[_T]]: ...

class Container:
    registrations: _Registry
    def __init__(self, registrations: _Registry | None = None, auto_register: bool = False) -> None: ...

    # all kwargs are forwarded to _Registration.builder
    @overload
    def register(self, service: type[_T] | str, *, instance: _T, cache: bool = True, **kwargs: Any) -> Self: ...
    @overload
    def register(
        self,
        service: type[_T] | str,
        factory: Callable[..., _T] | _Empty = ...,
        *,
        scope: Scope = Scope.transient,
        cache: bool = True,
        **kwargs: Any,
    ) -> Self: ...
    @overload
    def register(
        self,
        service: type[_T] | str,
        factory: Callable[..., _T] | _Empty = ...,
        instance: _T | _Empty = ...,
        scope: Scope = Scope.transient,
        cache: bool = True,
        **kwargs: Any,
    ) -> Self: ...

    def resolve_all(self, service: type[_T] | str, **kwargs: Any) -> list[_T]: ...
    def resolve(self, service_key: type[_T] | str, **kwargs: Any) -> _T: ...
    def instantiate(self, service_key: type[_T] | str, **kwargs: Any) -> _T: ...
    def child(self) -> Self: ...
