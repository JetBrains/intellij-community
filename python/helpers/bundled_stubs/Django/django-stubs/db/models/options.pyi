from collections.abc import Iterable, Sequence
from typing import Any, Generic, Literal, TypeVar, overload

from django.apps.config import AppConfig
from django.apps.registry import Apps
from django.contrib.contenttypes.fields import GenericForeignKey
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.base import Model
from django.db.models.constraints import BaseConstraint, UniqueConstraint
from django.db.models.fields import AutoField, Field
from django.db.models.fields.related import ManyToManyField, OneToOneField
from django.db.models.fields.reverse_related import ForeignObjectRel
from django.db.models.manager import Manager
from django.db.models.query_utils import PathInfo
from django.utils.datastructures import ImmutableList, _ListOrTuple
from django.utils.functional import _StrOrPromise, cached_property
from typing_extensions import TypeAlias

PROXY_PARENTS: object
EMPTY_RELATION_TREE: Any
IMMUTABLE_WARNING: str
DEFAULT_NAMES: tuple[str, ...]

_OptionTogetherT: TypeAlias = _ListOrTuple[_ListOrTuple[str] | str] | set[tuple[str, ...]]  # noqa: PYI047

@overload
def normalize_together(option_together: _ListOrTuple[_ListOrTuple[str] | str]) -> tuple[tuple[str, ...], ...]: ...

# Any other value will be returned unchanged, but probably only set is semantically allowed
@overload
def normalize_together(option_together: set[tuple[str, ...]]) -> set[tuple[str, ...]]: ...

_T = TypeVar("_T")

def make_immutable_fields_list(name: str, data: Iterable[_T]) -> ImmutableList[_T]: ...

_M = TypeVar("_M", bound=Model)

class Options(Generic[_M]):
    constraints: list[BaseConstraint]
    FORWARD_PROPERTIES: set[str]
    REVERSE_PROPERTIES: set[str]
    default_apps: Any
    local_fields: list[Field]
    local_many_to_many: list[ManyToManyField]
    private_fields: list[Any]
    local_managers: list[Manager]
    base_manager_name: str | None
    default_manager_name: str | None
    model_name: str | None
    verbose_name: _StrOrPromise | None
    verbose_name_plural: _StrOrPromise | None
    db_table: str
    ordering: Sequence[str] | None
    indexes: list[Any]
    unique_together: Sequence[tuple[str, ...]]  # Are always normalized
    index_together: Sequence[tuple[str, ...]]  # Are always normalized
    select_on_save: bool
    default_permissions: Sequence[str]
    permissions: list[Any]
    object_name: str | None
    app_label: str
    get_latest_by: Sequence[str] | None
    order_with_respect_to: str | None
    db_tablespace: str
    required_db_features: list[str]
    required_db_vendor: Literal["sqlite", "postgresql", "mysql", "oracle"] | None
    meta: type | None
    pk: Field
    auto_field: AutoField | None
    abstract: bool
    managed: bool
    proxy: bool
    proxy_for_model: type[Model] | None
    concrete_model: type[Model] | None
    swappable: str | None
    parents: dict[type[Model], GenericForeignKey | Field]
    auto_created: bool
    related_fkey_lookups: list[Any]
    apps: Apps
    default_related_name: str | None
    model: type[Model]
    original_attrs: dict[str, Any]
    def __init__(self, meta: type | None, app_label: str | None = None) -> None: ...
    @property
    def label(self) -> str: ...
    @property
    def label_lower(self) -> str: ...
    @property
    def app_config(self) -> AppConfig: ...
    @property
    def installed(self) -> bool: ...
    def contribute_to_class(self, cls: type[Model], name: str) -> None: ...
    def add_manager(self, manager: Manager) -> None: ...
    def add_field(self, field: GenericForeignKey | Field[Any, Any], private: bool = False) -> None: ...
    # if GenericForeignKey is passed as argument, it has primary_key = True set before
    def setup_pk(self, field: GenericForeignKey | Field[Any, Any]) -> None: ...
    def setup_proxy(self, target: type[Model]) -> None: ...
    def can_migrate(self, connection: BaseDatabaseWrapper | str) -> bool: ...
    @property
    def verbose_name_raw(self) -> str: ...
    @property
    def swapped(self) -> str | None: ...
    @cached_property
    def fields_map(self) -> dict[str, Field[Any, Any] | ForeignObjectRel]: ...
    @cached_property
    def managers(self) -> ImmutableList[Manager]: ...
    @cached_property
    def managers_map(self) -> dict[str, Manager]: ...
    @cached_property
    def base_manager(self) -> Manager: ...
    @cached_property
    def default_manager(self) -> Manager | None: ...
    @cached_property
    def fields(self) -> ImmutableList[Field[Any, Any]]: ...
    def get_field(self, field_name: str) -> Field | ForeignObjectRel | GenericForeignKey: ...
    def get_base_chain(self, model: type[Model]) -> list[type[Model]]: ...
    def get_parent_list(self) -> list[type[Model]]: ...
    def get_ancestor_link(self, ancestor: type[Model]) -> OneToOneField | None: ...
    def get_path_to_parent(self, parent: type[Model]) -> list[PathInfo]: ...
    def get_path_from_parent(self, parent: type[Model]) -> list[PathInfo]: ...
    def get_fields(
        self, include_parents: bool = True, include_hidden: bool = False
    ) -> list[Field[Any, Any] | ForeignObjectRel | GenericForeignKey]: ...
    def _get_fields(
        self,
        forward: bool = True,
        reverse: bool = True,
        include_parents: bool | object = True,
        include_hidden: bool = False,
        topmost_call: bool = True,
    ) -> list[Field[Any, Any] | ForeignObjectRel | GenericForeignKey]: ...
    @cached_property
    def total_unique_constraints(self) -> list[UniqueConstraint]: ...
    @cached_property
    def db_returning_fields(self) -> list[Field[Any, Any]]: ...
