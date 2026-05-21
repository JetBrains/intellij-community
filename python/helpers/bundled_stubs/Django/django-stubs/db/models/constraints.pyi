from collections.abc import Sequence
from enum import Enum
from typing import Any, cast, overload

from django.core.checks import CheckMessage
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.models.base import Model
from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.query_utils import Q
from django.utils.functional import _StrOrPromise
from typing_extensions import Self, override

class Deferrable(Enum):
    DEFERRED = cast(str, ...)
    IMMEDIATE = cast(str, ...)

class BaseConstraint:
    name: str
    violation_error_code: str | None
    violation_error_message: _StrOrPromise | None
    default_violation_error_message: _StrOrPromise
    non_db_attrs: tuple[str, ...]
    def __init__(
        self,
        *,
        name: str,
        violation_error_code: str | None = None,
        violation_error_message: _StrOrPromise | None = None,
    ) -> None: ...
    @property
    def contains_expressions(self) -> bool: ...
    def constraint_sql(self, model: type[Model] | None, schema_editor: BaseDatabaseSchemaEditor | None) -> str: ...
    def create_sql(self, model: type[Model] | None, schema_editor: BaseDatabaseSchemaEditor | None) -> str: ...
    def remove_sql(self, model: type[Model] | None, schema_editor: BaseDatabaseSchemaEditor | None) -> str: ...
    def validate(
        self, model: type[Model], instance: Model, exclude: set[str] | None = None, using: str = "default"
    ) -> None: ...
    def get_violation_error_message(self) -> _StrOrPromise | dict[str, Any]: ...
    def check(self, model: type[Model], connection: BaseDatabaseWrapper) -> list[CheckMessage]: ...
    def deconstruct(self) -> tuple[str, Sequence[Any], dict[str, Any]]: ...
    def clone(self) -> Self: ...

class CheckConstraint(BaseConstraint):
    condition: Q | BaseExpression

    def __init__(
        self,
        *,
        name: str,
        condition: Q | BaseExpression,
        violation_error_code: str | None = None,
        violation_error_message: _StrOrPromise | None = None,
    ) -> None: ...
    @override
    def check(self, model: type[Model], connection: BaseDatabaseWrapper) -> list[CheckMessage]: ...
    @override
    def validate(
        self, model: type[Model], instance: Model, exclude: set[str] | None = None, using: str = "default"
    ) -> None: ...

class UniqueConstraint(BaseConstraint):
    expressions: Sequence[BaseExpression | Combinable]
    fields: Sequence[str]
    condition: Q | None
    deferrable: Deferrable | None
    nulls_distinct: bool | None

    @overload
    def __init__(
        self,
        *expressions: str | BaseExpression | Combinable,
        fields: None = None,
        name: str | None = None,
        condition: Q | None = None,
        deferrable: Deferrable | None = None,
        include: Sequence[str] | None = None,
        opclasses: Sequence[Any] = (),
        nulls_distinct: bool | None = None,
        violation_error_code: str | None = None,
        violation_error_message: _StrOrPromise | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        *,
        fields: Sequence[str],
        name: str | None = None,
        condition: Q | None = None,
        deferrable: Deferrable | None = None,
        include: Sequence[str] | None = None,
        opclasses: Sequence[Any] = (),
        nulls_distinct: bool | None = None,
        violation_error_code: str | None = None,
        violation_error_message: _StrOrPromise | None = None,
    ) -> None: ...
    @property
    @override
    def contains_expressions(self) -> bool: ...
    @override
    def check(self, model: type[Model], connection: BaseDatabaseWrapper) -> list[CheckMessage]: ...
    @override
    def validate(
        self, model: type[Model], instance: Model, exclude: set[str] | None = None, using: str = "default"
    ) -> None: ...

__all__ = ["BaseConstraint", "CheckConstraint", "Deferrable", "UniqueConstraint"]
