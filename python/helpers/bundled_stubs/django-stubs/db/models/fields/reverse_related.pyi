from collections.abc import Callable, Sequence
from typing import Any, Literal

from django.db.models.base import Model
from django.db.models.fields import AutoField, Field, _AllLimitChoicesTo, _ChoicesList, _LimitChoicesTo
from django.db.models.fields.related import ForeignKey, ForeignObject, ManyToManyField, OneToOneField
from django.db.models.lookups import Lookup, StartsWith, Transform
from django.db.models.query_utils import FilteredRelation, PathInfo
from django.db.models.sql.where import WhereNode
from django.utils.functional import cached_property

from .mixins import FieldCacheMixin

# Common note: `model` and `through` can be of type `str` when passed to `__init__`.
# When parent's `contribute_to_class` is called (during startup),
# strings are resolved to real model classes.
# Thus `str` is acceptable in __init__, but instance attribute `model` is always
# `Type[Model]`

class ForeignObjectRel(FieldCacheMixin):
    auto_created: bool
    concrete: Literal[False]
    editable: bool
    is_relation: bool
    null: bool
    field: ForeignObject
    model: type[Model]
    related_name: str | None
    related_query_name: str | None
    limit_choices_to: _AllLimitChoicesTo | None
    parent_link: bool
    on_delete: Callable
    symmetrical: bool
    multiple: bool
    field_name: str | None
    def __init__(
        self,
        field: ForeignObject,
        to: type[Model] | str,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: _AllLimitChoicesTo | None = ...,
        parent_link: bool = ...,
        on_delete: Callable = ...,
    ) -> None: ...
    @cached_property
    def hidden(self) -> bool: ...
    @cached_property
    def name(self) -> str: ...
    @property
    def remote_field(self) -> ForeignObject: ...
    @property
    def target_field(self) -> AutoField: ...
    @cached_property
    def related_model(self) -> type[Model] | Literal["self"]: ...
    @cached_property
    def many_to_many(self) -> bool: ...
    @cached_property
    def many_to_one(self) -> bool: ...
    @cached_property
    def one_to_many(self) -> bool: ...
    @cached_property
    def one_to_one(self) -> bool: ...
    def get_lookup(self, lookup_name: str) -> type[Lookup] | None: ...
    def get_lookups(self) -> dict[str, Any]: ...
    def get_transform(self, name: str) -> type[Transform] | None: ...
    def get_internal_type(self) -> str: ...
    @property
    def db_type(self) -> Any: ...
    # Yes, seems that `get_choices` will fail if `limit_choices_to=None`
    # and `self.limit_choices_to` is callable.
    def get_choices(
        self,
        include_blank: bool = ...,
        blank_choice: _ChoicesList = ...,
        limit_choices_to: _LimitChoicesTo | None = ...,
        ordering: Sequence[str] = ...,
    ) -> _ChoicesList: ...
    def is_hidden(self) -> bool: ...
    def get_joining_columns(self) -> tuple: ...
    def get_joining_fields(self) -> tuple[tuple[Field, Field], ...]: ...
    def get_extra_restriction(
        self, where_class: type[WhereNode], alias: str, related_alias: str
    ) -> StartsWith | WhereNode | None: ...
    def set_field_name(self) -> None: ...
    def get_accessor_name(self, model: type[Model] | None = ...) -> str | None: ...
    def get_path_info(self, filtered_relation: FilteredRelation | None = ...) -> list[PathInfo]: ...

class ManyToOneRel(ForeignObjectRel):
    field: ForeignKey
    def __init__(
        self,
        field: ForeignKey,
        to: type[Model] | str,
        field_name: str,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: _AllLimitChoicesTo | None = ...,
        parent_link: bool = ...,
        on_delete: Callable = ...,
    ) -> None: ...
    def get_related_field(self) -> Field: ...
    def get_accessor_name(self, model: type[Model] | None = ...) -> str: ...

class OneToOneRel(ManyToOneRel):
    field: OneToOneField
    def __init__(
        self,
        field: OneToOneField,
        to: type[Model] | str,
        field_name: str | None,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: _AllLimitChoicesTo | None = ...,
        parent_link: bool = ...,
        on_delete: Callable = ...,
    ) -> None: ...

class ManyToManyRel(ForeignObjectRel):
    field: ManyToManyField[Any, Any]  # type: ignore[assignment]
    through: type[Model] | None
    through_fields: tuple[str, str] | None
    db_constraint: bool
    def __init__(
        self,
        field: ManyToManyField[Any, Any],
        to: type[Model] | str,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: _AllLimitChoicesTo | None = ...,
        symmetrical: bool = ...,
        through: type[Model] | str | None = ...,
        through_fields: tuple[str, str] | None = ...,
        db_constraint: bool = ...,
    ) -> None: ...
    def get_related_field(self) -> Field: ...
