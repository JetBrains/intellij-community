from collections.abc import Callable
from typing import Any

from django.contrib.contenttypes.models import ContentType
from django.core.checks.messages import CheckMessage
from django.db.models.base import Model
from django.db.models.expressions import Combinable
from django.db.models.fields import Field, _AllLimitChoicesTo
from django.db.models.fields.mixins import FieldCacheMixin
from django.db.models.fields.related import ForeignObject
from django.db.models.fields.related_descriptors import ReverseManyToOneDescriptor
from django.db.models.fields.reverse_related import ForeignObjectRel
from django.db.models.query import QuerySet
from django.db.models.query_utils import FilteredRelation, PathInfo
from django.db.models.sql.where import WhereNode
from django.utils.functional import cached_property
from typing_extensions import override

class GenericForeignKey(FieldCacheMixin, Field):
    # django-stubs implementation only fields
    _pyi_private_set_type: Any | Combinable
    _pyi_private_get_type: Any
    # attributes
    hidden: bool
    is_relation: bool
    many_to_many: bool
    many_to_one: bool
    one_to_many: bool
    one_to_one: bool
    related_model: Any
    ct_field: str
    fk_field: str
    for_concrete_model: bool
    column: None
    def __init__(
        self, ct_field: str = "content_type", fk_field: str = "object_id", for_concrete_model: bool = True
    ) -> None: ...
    name: Any
    model: Any
    @override
    def contribute_to_class(self, cls: type[Model], name: str, **kwargs: Any) -> None: ...  # type: ignore[override]
    @override
    def get_attname_column(self) -> tuple[str, None]: ...
    @cached_property
    def ct_field_attname(self) -> str: ...
    @override
    def get_filter_kwargs_for_object(self, obj: Model) -> dict[str, ContentType | None]: ...
    def get_forward_related_filter(self, obj: Model) -> dict[str, int]: ...
    @override
    def check(self, **kwargs: Any) -> list[CheckMessage]: ...
    def get_content_type(
        self,
        obj: Model | None = None,
        id: int | None = None,
        using: str | None = None,
        model: type[Model] | None = None,
    ) -> ContentType: ...
    def get_prefetch_querysets(
        self, instances: list[Model] | QuerySet, querysets: list[QuerySet] | None = None
    ) -> tuple[list[Model], Callable[..., Any], Callable[..., Any], bool, str, bool]: ...
    @override
    def __get__(self, instance: Model | None, cls: type[Model] | None = ...) -> Any | None: ...  # type: ignore[override]
    @override
    def __set__(self, instance: Model, value: Any | None) -> None: ...

class GenericRel(ForeignObjectRel):
    field: GenericRelation
    def __init__(
        self,
        field: GenericRelation,
        to: type[Model] | str,
        related_name: str | None = None,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
    ) -> None: ...

class GenericRelation(ForeignObject[Any, Any]):
    rel_class: type[GenericRel]
    mti_inherited: bool
    object_id_field_name: str
    content_type_field_name: str
    for_concrete_model: bool
    def __init__(
        self,
        to: type[Model] | str,
        object_id_field: str = "object_id",
        content_type_field: str = "content_type",
        for_concrete_model: bool = True,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        **kwargs: Any,
    ) -> None: ...
    @override
    def resolve_related_fields(self) -> list[tuple[Field, Field]]: ...
    @override
    def get_local_related_value(self, instance: Model) -> tuple[Any, ...]: ...
    @override
    def get_foreign_related_value(self, instance: Model) -> tuple[Any, ...]: ...
    @override
    def get_path_info(self, filtered_relation: FilteredRelation | None = None) -> list[PathInfo]: ...
    @override
    def get_reverse_path_info(self, filtered_relation: FilteredRelation | None = None) -> list[PathInfo]: ...
    @override
    def contribute_to_class(self, cls: type[Model], name: str, **kwargs: Any) -> None: ...  # type: ignore[override]
    def get_content_type(self) -> ContentType: ...
    @override
    def get_extra_restriction(self, alias: str | None, remote_alias: str) -> WhereNode: ...
    def bulk_related_objects(self, objs: list[Model], using: str = "default") -> QuerySet: ...

class ReverseGenericManyToOneDescriptor(ReverseManyToOneDescriptor): ...

def create_generic_related_manager(superclass: Any, rel: Any) -> type[Any]: ...  # GenericRelatedObjectManager
