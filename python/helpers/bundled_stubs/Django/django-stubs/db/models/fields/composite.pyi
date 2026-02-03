from collections.abc import Iterable, Iterator, Mapping
from typing import Any, Literal

from django.contrib.contenttypes.fields import GenericForeignKey
from django.core import validators  # due to weird mypy.stubtest error
from django.db.models import NOT_PROVIDED, Field
from django.db.models.base import Model
from django.db.models.fields.reverse_related import ForeignObjectRel
from django.utils.choices import _ChoicesInput
from django.utils.functional import _StrOrPromise, cached_property

class AttributeSetter:
    def __init__(self, name: str, value: Any) -> None: ...

class CompositeAttribute:
    field: CompositePrimaryKey
    def __init__(self, field: CompositePrimaryKey) -> None: ...
    @property
    def attnames(self) -> list[str]: ...
    def __get__(self, instance: Model, cls: type[Model] | None = None) -> tuple[Any, ...]: ...
    def __set__(self, instance: Model, values: list[Any] | tuple[Any] | None) -> None: ...

class CompositePrimaryKey(Field):
    field_names: tuple[str]
    descriptor_class: type[CompositeAttribute]
    def __init__(
        self,
        *args: str,
        verbose_name: _StrOrPromise | None = None,
        name: str | None = None,
        primary_key: Literal[True] = True,
        max_length: int | None = None,
        unique: bool = False,
        blank: Literal[True] = True,
        null: bool = False,
        db_index: bool = False,
        rel: ForeignObjectRel | None = None,
        default: type[NOT_PROVIDED] = ...,
        editable: Literal[False] = False,
        serialize: bool = True,
        unique_for_date: str | None = None,
        unique_for_month: str | None = None,
        unique_for_year: str | None = None,
        choices: _ChoicesInput | None = None,
        help_text: _StrOrPromise = "",
        db_column: None = None,
        db_tablespace: str | None = None,
        auto_created: bool = False,
        validators: Iterable[validators._ValidatorCallable] = (),
        error_messages: Mapping[str, _StrOrPromise] | None = None,
        db_comment: str | None = None,
        db_default: type[NOT_PROVIDED] = ...,
    ) -> None: ...
    @cached_property
    def fields(
        self,
    ) -> tuple[Field | ForeignObjectRel | GenericForeignKey, ...]: ...
    @cached_property
    def columns(self) -> tuple[str, ...]: ...
    def __iter__(self) -> Iterator[Field | ForeignObjectRel | GenericForeignKey]: ...
    def __len__(self) -> int: ...
    def get_pk_value_on_save(self, instance: Model) -> tuple: ...  # actual type is tuple of field.value_from_object

def unnest(fields: Iterable[Field[Any, Any]]) -> list[Field[Any, Any]]: ...
