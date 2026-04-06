import sys
from collections.abc import Collection, Iterable, Sequence
from typing import Any, ClassVar, Final, TypeVar, overload

from django.core.checks.messages import CheckMessage
from django.core.exceptions import MultipleObjectsReturned as BaseMultipleObjectsReturned
from django.core.exceptions import ObjectDoesNotExist, ObjectNotUpdated, ValidationError
from django.db.models import BaseConstraint, Field, QuerySet
from django.db.models.manager import Manager
from django.db.models.options import Options
from django.db.models.utils import AltersData
from typing_extensions import Self, override

_Self = TypeVar("_Self", bound=Model)

class Deferred: ...

DEFERRED: Deferred

class ModelStateFieldsCacheDescriptor:
    @overload
    def __get__(self, instance: None, cls: type[ModelState] | None = None) -> Self: ...
    @overload
    def __get__(self, instance: ModelState, cls: type[ModelState] | None = None) -> dict[Any, Any]: ...

class ModelState:
    db: str | None
    adding: bool
    fields_cache: ModelStateFieldsCacheDescriptor

class ModelBase(type):
    def __new__(cls, name: str, bases: tuple[type, ...], attrs: dict[str, Any], **kwargs: Any) -> ModelBase: ...
    def add_to_class(cls, name: str, value: Any) -> None: ...
    @property
    def _base_manager(cls: type[_Self]) -> Manager[_Self]: ...  # type: ignore[misc]
    @property
    def _default_manager(cls: type[_Self]) -> Manager[_Self]: ...  # type: ignore[misc]

class Model(AltersData, metaclass=ModelBase):
    # Note: these two metaclass generated attributes don't really exist on the 'Model'
    # class, runtime they are only added on concrete subclasses of 'Model'. The
    # metaclass also sets up correct inheritance from concrete parent models exceptions.
    # Our mypy plugin aligns with this behaviour and will remove the 2 attributes below
    # and re-add them to correct concrete subclasses of 'Model'
    DoesNotExist: Final[type[ObjectDoesNotExist]]
    NotUpdated: Final[type[ObjectNotUpdated]]
    MultipleObjectsReturned: Final[type[BaseMultipleObjectsReturned]]
    # This 'objects' attribute will be deleted, via the plugin, in favor of managing it
    # to only exist on subclasses it exists on during runtime.
    objects: ClassVar[Manager[Self]]

    _meta: ClassVar[Options[Self]]
    pk: Any
    _state: ModelState
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    @classmethod
    def from_db(cls, db: str | None, field_names: Collection[str], values: Collection[Any]) -> Self: ...
    if sys.version_info >= (3, 11):
        @override
        def __getstate__(self) -> dict: ...
    else:
        def __getstate__(self) -> dict: ...
    def _get_pk_val(self, meta: Options[Model] | None = None) -> str: ...
    def get_deferred_fields(self) -> set[str]: ...
    def refresh_from_db(
        self,
        using: str | None = None,
        fields: Iterable[str] | None = None,
        from_queryset: QuerySet[Self] | None = None,
    ) -> None: ...
    async def arefresh_from_db(
        self,
        using: str | None = None,
        fields: Iterable[str] | None = None,
        from_queryset: QuerySet[Self] | None = None,
    ) -> None: ...
    def serializable_value(self, field_name: str) -> Any: ...
    def save(
        self,
        *,
        force_insert: bool | tuple[ModelBase, ...] = False,
        force_update: bool = False,
        using: str | None = None,
        update_fields: Iterable[str] | None = None,
    ) -> None: ...
    async def asave(
        self,
        *,
        force_insert: bool | tuple[ModelBase, ...] = False,
        force_update: bool = False,
        using: str | None = None,
        update_fields: Iterable[str] | None = None,
    ) -> None: ...
    def save_base(
        self,
        raw: bool = False,
        force_insert: bool | tuple[ModelBase, ...] = False,
        force_update: bool = False,
        using: str | None = None,
        update_fields: Iterable[str] | None = None,
    ) -> None: ...
    def _do_update(
        self,
        base_qs: QuerySet[Self],
        using: str | None,
        pk_val: Any,
        values: Collection[tuple[Field, type[Model] | None, Any]],
        update_fields: Iterable[str] | None,
        forced_update: bool,
        returning_fields: Sequence[Field[Any, Any]],
    ) -> list[Sequence[Any]]: ...
    def delete(self, using: Any | None = None, keep_parents: bool = False) -> tuple[int, dict[str, int]]: ...
    async def adelete(self, using: Any | None = None, keep_parents: bool = False) -> tuple[int, dict[str, int]]: ...
    def prepare_database_save(self, field: Field) -> Any: ...
    def clean(self) -> None: ...
    def validate_unique(self, exclude: Collection[str] | None = None) -> None: ...
    def date_error_message(self, lookup_type: str, field_name: str, unique_for: str) -> ValidationError: ...
    def unique_error_message(self, model_class: type[Model], unique_check: Sequence[str]) -> ValidationError: ...
    def get_constraints(self) -> list[tuple[type[Model], Sequence[BaseConstraint]]]: ...
    def validate_constraints(self, exclude: Collection[str] | None = None) -> None: ...
    def full_clean(
        self, exclude: Iterable[str] | None = None, validate_unique: bool = True, validate_constraints: bool = True
    ) -> None: ...
    def clean_fields(self, exclude: Collection[str] | None = None) -> None: ...
    @classmethod
    def check(cls, **kwargs: Any) -> list[CheckMessage]: ...

def subclass_exception(
    name: str, bases: tuple[type[BaseException], ...], module: str, attached_to: type[Model]
) -> type[BaseException]: ...
def method_set_order(
    self: Model, ordered_obj: type[Model], id_list: Iterable[Any], using: str | None = None
) -> None: ...
def method_get_order(self: Model, ordered_obj: type[Model]) -> QuerySet[Any]: ...
def make_foreign_order_accessors(model: type[Model], related_model: type[Model]) -> None: ...
def model_unpickle(model_id: tuple[str, str] | type[Model]) -> Model: ...
