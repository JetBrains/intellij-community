from collections.abc import Iterable, Sequence
from typing import Any, TypeVar

from _typeshed import Unused
from django.core.validators import _ValidatorCallable
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Field
from django.db.models.expressions import Combinable, Expression
from django.db.models.fields import NOT_PROVIDED, _ErrorMessagesDict, _ErrorMessagesMapping
from django.db.models.fields.mixins import CheckFieldDefaultMixin
from django.db.models.lookups import Transform
from django.utils.choices import _Choices
from django.utils.functional import _StrOrPromise

# __set__ value type
_ST = TypeVar("_ST")
# __get__ return type
_GT = TypeVar("_GT")

class ArrayField(CheckFieldDefaultMixin, Field[_ST, _GT]):
    _pyi_private_set_type: Sequence[Any] | Combinable
    _pyi_private_get_type: list[Any]

    empty_strings_allowed: bool
    default_error_messages: _ErrorMessagesDict
    base_field: Field
    size: int | None
    default_validators: Sequence[_ValidatorCallable]
    from_db_value: Any
    def __init__(
        self,
        base_field: Field,
        size: int | None = ...,
        *,
        verbose_name: _StrOrPromise | None = ...,
        name: str | None = ...,
        primary_key: bool = ...,
        max_length: int | None = ...,
        unique: bool = ...,
        blank: bool = ...,
        null: bool = ...,
        db_index: bool = ...,
        default: Any = ...,
        db_default: type[NOT_PROVIDED] | Expression | _ST = ...,
        editable: bool = ...,
        auto_created: bool = ...,
        serialize: bool = ...,
        unique_for_date: str | None = ...,
        unique_for_month: str | None = ...,
        unique_for_year: str | None = ...,
        choices: _Choices | None = ...,
        help_text: _StrOrPromise = ...,
        db_column: str | None = ...,
        db_comment: str | None = ...,
        db_tablespace: str | None = ...,
        validators: Iterable[_ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
    ) -> None: ...
    @property
    def description(self) -> str: ...  # type: ignore[override]
    def cast_db_type(self, connection: BaseDatabaseWrapper) -> str: ...
    def get_placeholder(self, value: Unused, compiler: Unused, connection: BaseDatabaseWrapper) -> str: ...
    def get_transform(self, name: str) -> type[Transform] | None: ...
