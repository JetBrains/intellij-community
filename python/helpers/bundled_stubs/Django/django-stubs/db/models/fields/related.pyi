from collections.abc import Callable, Iterable, Sequence
from typing import Any, Generic, Literal, TypeVar, overload
from uuid import UUID

from django.core import validators  # due to weird mypy.stubtest error
from django.db.models.base import Model
from django.db.models.expressions import Combinable, Expression
from django.db.models.fields import NOT_PROVIDED, Field, _AllLimitChoicesTo, _ErrorMessagesMapping, _LimitChoicesTo
from django.db.models.fields.mixins import FieldCacheMixin
from django.db.models.fields.related_descriptors import ForwardManyToOneDescriptor as ForwardManyToOneDescriptor
from django.db.models.fields.related_descriptors import ForwardOneToOneDescriptor as ForwardOneToOneDescriptor
from django.db.models.fields.related_descriptors import ManyRelatedManager
from django.db.models.fields.related_descriptors import ManyToManyDescriptor as ManyToManyDescriptor
from django.db.models.fields.related_descriptors import ReverseManyToOneDescriptor as ReverseManyToOneDescriptor
from django.db.models.fields.related_descriptors import ReverseOneToOneDescriptor as ReverseOneToOneDescriptor
from django.db.models.fields.reverse_related import ForeignObjectRel as ForeignObjectRel
from django.db.models.fields.reverse_related import ManyToManyRel as ManyToManyRel
from django.db.models.fields.reverse_related import ManyToOneRel as ManyToOneRel
from django.db.models.fields.reverse_related import OneToOneRel as OneToOneRel
from django.db.models.query_utils import FilteredRelation, PathInfo, Q
from django.utils.choices import _Choices
from django.utils.functional import _StrOrPromise, cached_property
from typing_extensions import Self

RECURSIVE_RELATIONSHIP_CONSTANT: Literal["self"]

def resolve_relation(scope_model: type[Model], relation: str | type[Model]) -> str | type[Model]: ...

# __set__ value type
_ST = TypeVar("_ST", contravariant=True)
# __get__ return type
_GT = TypeVar("_GT", covariant=True, default=_ST)

class RelatedField(FieldCacheMixin, Field[_ST, _GT]):
    one_to_many: bool
    one_to_one: bool
    many_to_many: bool
    many_to_one: bool
    opts: Any

    remote_field: ForeignObjectRel
    rel_class: type[ForeignObjectRel]
    swappable: bool
    def __init__(
        self,
        related_name: str | None = None,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        *,
        verbose_name: _StrOrPromise | None = ...,
        name: str | None = ...,
        primary_key: bool = ...,
        max_length: int | None = ...,
        unique: bool = ...,
        blank: bool = ...,
        null: bool = ...,
        db_index: bool = ...,
        rel: ForeignObjectRel | None = ...,
        default: Any = ...,
        db_default: type[NOT_PROVIDED] | Expression | _ST = ...,
        editable: bool = ...,
        serialize: bool = ...,
        unique_for_date: str | None = ...,
        unique_for_month: str | None = ...,
        unique_for_year: str | None = ...,
        choices: _Choices | None = ...,
        help_text: _StrOrPromise = ...,
        db_column: str | None = ...,
        db_tablespace: str | None = ...,
        auto_created: bool = ...,
        validators: Iterable[validators._ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        db_comment: str | None = ...,
    ) -> None: ...
    @cached_property
    def related_model(self) -> type[Model] | Literal["self"]: ...  # type: ignore[override]
    def get_forward_related_filter(self, obj: Model) -> dict[str, int | UUID]: ...
    def get_reverse_related_filter(self, obj: Model) -> Q: ...
    @property
    def swappable_setting(self) -> str | None: ...
    def set_attributes_from_rel(self) -> None: ...
    def do_related_class(self, other: type[Model], cls: type[Model]) -> None: ...
    def get_limit_choices_to(self) -> _LimitChoicesTo: ...
    def related_query_name(self) -> str: ...
    @property
    def target_field(self) -> Field: ...

class ForeignObject(RelatedField[_ST, _GT]):
    remote_field: ForeignObjectRel
    rel_class: type[ForeignObjectRel]
    from_fields: Sequence[str]
    to_fields: Sequence[str | None]  # None occurs in ForeignKey, where to_field defaults to None
    swappable: bool
    def __init__(
        self,
        to: type[Model] | str,
        on_delete: Callable[..., None],
        from_fields: Sequence[str],
        to_fields: Sequence[str],
        rel: ForeignObjectRel | None = None,
        related_name: str | None = None,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        parent_link: bool = False,
        swappable: bool = True,
        *,
        verbose_name: _StrOrPromise | None = ...,
        name: str | None = ...,
        primary_key: bool = ...,
        unique: bool = ...,
        blank: bool = ...,
        null: bool = ...,
        db_index: bool = ...,
        default: Any = ...,
        db_default: type[NOT_PROVIDED] | Expression | _ST = ...,
        editable: bool = ...,
        auto_created: bool = ...,
        serialize: bool = ...,
        choices: _Choices | None = ...,
        help_text: _StrOrPromise = ...,
        db_column: str | None = ...,
        db_tablespace: str | None = ...,
        validators: Iterable[validators._ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        db_comment: str | None = ...,
    ) -> None: ...
    # class access
    @overload
    def __get__(self, instance: None, owner: Any) -> ForwardManyToOneDescriptor[Self]: ...
    # Model instance access
    @overload
    def __get__(self, instance: Model, owner: Any) -> _GT: ...
    # non-Model instances
    @overload
    def __get__(self, instance: Any, owner: Any) -> Self: ...
    def resolve_related_fields(self) -> list[tuple[Field, Field]]: ...
    @cached_property
    def related_fields(self) -> list[tuple[Field, Field]]: ...
    @cached_property
    def reverse_related_fields(self) -> list[tuple[Field, Field]]: ...
    @cached_property
    def local_related_fields(self) -> tuple[Field, ...]: ...
    @cached_property
    def foreign_related_fields(self) -> tuple[Field, ...]: ...
    def get_joining_fields(self, reverse_join: bool = False) -> tuple[tuple[Field, Field], ...]: ...
    def get_reverse_joining_fields(self) -> tuple[tuple[Field, Field], ...]: ...

class ForeignKey(ForeignObject[_ST, _GT]):
    _pyi_private_set_type: Any | Combinable
    _pyi_private_get_type: Any

    remote_field: ManyToOneRel
    rel_class: type[ManyToOneRel]
    def __init__(
        self,
        to: type[Model] | str,
        on_delete: Callable[..., None],
        related_name: str | None = None,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        parent_link: bool = False,
        to_field: str | None = None,
        db_constraint: bool = True,
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
        db_tablespace: str | None = ...,
        validators: Iterable[validators._ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        db_comment: str | None = ...,
    ) -> None: ...

class OneToOneField(ForeignKey[_ST, _GT]):
    _pyi_private_set_type: Any | Combinable
    _pyi_private_get_type: Any

    remote_field: OneToOneRel
    rel_class: type[OneToOneRel]
    def __init__(
        self,
        to: type[Model] | str,
        on_delete: Any,
        to_field: str | None = None,
        *,
        related_name: str | None = ...,
        related_query_name: str | None = ...,
        limit_choices_to: _AllLimitChoicesTo | None = ...,
        parent_link: bool = ...,
        db_constraint: bool = ...,
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
        db_tablespace: str | None = ...,
        validators: Iterable[validators._ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        db_comment: str | None = ...,
    ) -> None: ...
    # class access
    @overload
    def __get__(self, instance: None, owner: Any) -> ForwardOneToOneDescriptor[Self]: ...
    # Model instance access
    @overload
    def __get__(self, instance: Model, owner: Any) -> _GT: ...
    # non-Model instances
    @overload
    def __get__(self, instance: Any, owner: Any) -> Self: ...

_Through = TypeVar("_Through", bound=Model)
_To = TypeVar("_To", bound=Model)

class ManyToManyField(RelatedField[Any, Any], Generic[_To, _Through]):
    description: _StrOrPromise
    has_null_arg: bool
    swappable: bool

    many_to_many: Literal[True]
    many_to_one: Literal[False]
    one_to_many: Literal[False]
    one_to_one: Literal[False]

    remote_field: ManyToManyRel
    rel_class: type[ManyToManyRel]
    def __init__(
        self,
        to: type[_To] | str,
        related_name: str | None = None,
        related_query_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        symmetrical: bool | None = None,
        through: type[_Through] | str | None = None,
        through_fields: tuple[str, str] | None = None,
        db_constraint: bool = True,
        db_table: str | None = None,
        swappable: bool = True,
        *,
        verbose_name: _StrOrPromise | None = ...,
        name: str | None = ...,
        primary_key: bool = ...,
        max_length: int | None = ...,
        unique: bool = ...,
        blank: bool = ...,
        db_index: bool = ...,
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
        db_tablespace: str | None = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        db_comment: str | None = ...,
    ) -> None: ...
    # class access
    @overload
    def __get__(self, instance: None, owner: Any) -> ManyToManyDescriptor[_To, _Through]: ...
    # Model instance access
    @overload
    def __get__(self, instance: Model, owner: Any) -> ManyRelatedManager[_To, _Through]: ...
    # non-Model instances
    @overload
    def __get__(self, instance: Any, owner: Any) -> Self: ...
    def get_path_info(self, filtered_relation: FilteredRelation | None = None) -> list[PathInfo]: ...
    def get_reverse_path_info(self, filtered_relation: FilteredRelation | None = None) -> list[PathInfo]: ...
    def contribute_to_related_class(self, cls: type[Model], related: RelatedField) -> None: ...
    def m2m_db_table(self) -> str: ...
    def m2m_column_name(self) -> str: ...
    def m2m_reverse_name(self) -> str: ...
    def m2m_field_name(self) -> str: ...
    def m2m_reverse_field_name(self) -> str: ...
    def m2m_target_field_name(self) -> str: ...
    def m2m_reverse_target_field_name(self) -> str: ...

def create_many_to_many_intermediary_model(field: ManyToManyField, klass: type[Model]) -> type[Model]: ...
