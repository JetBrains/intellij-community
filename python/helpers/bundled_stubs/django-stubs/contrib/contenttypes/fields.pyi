from collections.abc import Callable
from typing import Any

from django.contrib.contenttypes.models import ContentType
from django.core.checks.messages import CheckMessage
from django.db.models.base import Model
from django.db.models.expressions import Combinable
from django.db.models.fields import Field
from django.db.models.fields.mixins import FieldCacheMixin
from django.db.models.fields.related import ForeignObject
from django.db.models.fields.related_descriptors import ReverseManyToOneDescriptor
from django.db.models.fields.reverse_related import ForeignObjectRel
from django.db.models.query import QuerySet
from django.db.models.query_utils import FilteredRelation, PathInfo
from django.db.models.sql.where import WhereNode

class GenericForeignKey(FieldCacheMixin):
    # django-stubs implementation only fields
    _pyi_private_set_type: Any | Combinable
    _pyi_private_get_type: Any
    # attributes
    auto_created: bool
    concrete: bool
    editable: bool
    hidden: bool
    is_relation: bool
    many_to_many: bool
    many_to_one: bool
    one_to_many: bool
    one_to_one: bool
    related_model: Any
    remote_field: Any
    ct_field: str
    fk_field: str
    for_concrete_model: bool
    rel: None
    column: None
    def __init__(self, ct_field: str = ..., fk_field: str = ..., for_concrete_model: bool = ...) -> None: ...
    name: Any
    model: Any
    def contribute_to_class(self, cls: type[Model], name: str, **kwargs: Any) -> None: ...
    def get_filter_kwargs_for_object(self, obj: Model) -> dict[str, ContentType | None]: ...
    def get_forward_related_filter(self, obj: Model) -> dict[str, int]: ...
    def check(self, **kwargs: Any) -> list[CheckMessage]: ...
    def get_cache_name(self) -> str: ...
    def get_content_type(
        self, obj: Model | None = ..., id: int | None = ..., using: str | None = ..., model: type[Model] | None = ...
    ) -> ContentType: ...
    def get_prefetch_queryset(
        self, instances: list[Model] | QuerySet, queryset: QuerySet | None = ...
    ) -> tuple[list[Model], Callable, Callable, bool, str, bool]: ...
    def get_prefetch_querysets(
        self, instances: list[Model] | QuerySet, querysets: list[QuerySet] | None = ...
    ) -> tuple[list[Model], Callable, Callable, bool, str, bool]: ...
    def __get__(self, instance: Model | None, cls: type[Model] | None = ...) -> Any | None: ...
    def __set__(self, instance: Model, value: Any | None) -> None: ...

class GenericRel(ForeignObjectRel):
    field: GenericRelation
    def __init__(
        self,
        field: GenericRelation,
        to: type[Model] | str,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: dict[str, Any] | Callable[[], Any] | None = ...,
    ) -> None: ...

class GenericRelation(ForeignObject):
    rel_class: Any
    mti_inherited: bool
    object_id_field_name: str
    content_type_field_name: str
    for_concrete_model: bool
    to_fields: Any
    def __init__(
        self,
        to: type[Model] | str,
        object_id_field: str = ...,
        content_type_field: str = ...,
        for_concrete_model: bool = ...,
        related_query_name: str | None = ...,
        limit_choices_to: dict[str, Any] | Callable[[], Any] | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def resolve_related_fields(self) -> list[tuple[Field, Field]]: ...
    def get_path_info(self, filtered_relation: FilteredRelation | None = ...) -> list[PathInfo]: ...
    def get_reverse_path_info(self, filtered_relation: FilteredRelation | None = ...) -> list[PathInfo]: ...
    def get_content_type(self) -> ContentType: ...
    def get_extra_restriction(
        self, where_class: type[WhereNode], alias: str | None, remote_alias: str
    ) -> WhereNode: ...
    def bulk_related_objects(self, objs: list[Model], using: str = ...) -> QuerySet: ...

class ReverseGenericManyToOneDescriptor(ReverseManyToOneDescriptor): ...

def create_generic_related_manager(superclass: Any, rel: Any) -> type[Any]: ...  # GenericRelatedObjectManager
