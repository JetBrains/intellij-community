from typing import Any, ClassVar, Iterable, Literal

from django.core.validators import _ValidatorCallable
from django.db import models
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.expressions import Expression
from django.db.models.fields import _ErrorMessagesMapping
from django.db.models.sql import Query
from django.utils.choices import _Choices
from django.utils.datastructures import DictWrapper
from django.utils.functional import _StrOrPromise

class GeneratedField(models.Field):
    generated: ClassVar[Literal[True]]
    db_returning: Literal[True]
    _query: Query | None
    output_field: models.Field | None

    def __init__(
        self,
        *,
        expression: Expression,
        output_field: models.Field,
        db_persist: bool | None = ...,
        verbose_name: _StrOrPromise | None = ...,
        name: str | None = ...,
        primary_key: bool = ...,
        unique: bool = ...,
        blank: bool = ...,
        null: bool = ...,
        default: Any = ...,
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
        **kwargs: Any,
    ) -> None: ...
    def generated_sql(self, connection: BaseDatabaseWrapper) -> tuple[str, Any]: ...
    def db_type_parameters(self, connection: BaseDatabaseWrapper) -> DictWrapper: ...
