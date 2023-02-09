from _typeshed import Self, SupportsRichComparison
from collections import deque
from collections.abc import Callable, Container, Iterable, Sequence
from typing import Any
from typing_extensions import TypeAlias

from jsonschema import _utils, protocols
from jsonschema._types import TypeChecker

_RelevanceFuncType: TypeAlias = Callable[[ValidationError], SupportsRichComparison]

WEAK_MATCHES: frozenset[str]
STRONG_MATCHES: frozenset[str]

class _Error(Exception):
    message: str
    path: deque[str | int]
    relative_path: deque[str | int]
    schema_path: deque[str | int]
    relative_schema_path: deque[str | int]
    context: list[ValidationError] | None
    cause: Exception | None
    validator: protocols.Validator | None
    validator_value: Any
    instance: Any
    schema: Any
    parent: _Error | None
    def __init__(
        self,
        message: str,
        validator: _utils.Unset | None | protocols.Validator = ...,
        path: Sequence[str | int] = ...,
        cause: Any | None = ...,
        context: Sequence[ValidationError] = ...,
        validator_value=...,
        instance: Any = ...,
        schema: Any = ...,
        schema_path: Sequence[str | int] = ...,
        parent: _Error | None = ...,
        type_checker: _utils.Unset | TypeChecker = ...,
    ) -> None: ...
    @classmethod
    def create_from(cls: type[Self], other: _Error) -> Self: ...
    @property
    def absolute_path(self) -> Sequence[str | int]: ...
    @property
    def absolute_schema_path(self) -> Sequence[str | int]: ...
    @property
    def json_path(self) -> str: ...
    # TODO: this type could be made more precise using TypedDict to
    # enumerate the types of the members
    def _contents(self) -> dict[str, Any]: ...

class ValidationError(_Error): ...
class SchemaError(_Error): ...

class RefResolutionError(Exception):
    def __init__(self, cause: str) -> None: ...

class UndefinedTypeCheck(Exception):
    type: Any
    def __init__(self, type) -> None: ...

class UnknownType(Exception):
    type: Any
    instance: Any
    schema: Any
    def __init__(self, type, instance, schema) -> None: ...

class FormatError(Exception):
    message: Any
    cause: Any
    def __init__(self, message, cause: Any | None = ...) -> None: ...

class ErrorTree:
    errors: Any
    def __init__(self, errors=...) -> None: ...
    def __contains__(self, index): ...
    def __getitem__(self, index): ...
    def __setitem__(self, index, value) -> None: ...
    def __iter__(self): ...
    def __len__(self): ...
    @property
    def total_errors(self): ...

def by_relevance(weak: Container[str] = ..., strong: Container[str] = ...) -> _RelevanceFuncType: ...

relevance: _RelevanceFuncType

def best_match(errors: Iterable[ValidationError], key: _RelevanceFuncType = ...): ...
