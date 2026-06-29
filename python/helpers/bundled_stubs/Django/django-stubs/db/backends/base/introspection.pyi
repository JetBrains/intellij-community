from collections.abc import Iterable
from typing import Any, Literal, NamedTuple, type_check_only

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.backends.utils import CursorWrapper
from django.db.models.base import Model
from django.db.models.fields import Field
from typing_extensions import NotRequired, TypedDict

class TableInfo(NamedTuple):
    name: str
    type: str

class FieldInfo(NamedTuple):
    name: str
    type_code: int
    display_size: int
    internal_size: int
    precision: int
    scale: int
    null_ok: bool
    default: str
    collation: str

@type_check_only
class _ConstraintDict(TypedDict):
    columns: list[str]
    primary_key: bool
    unique: bool
    foreign_key: tuple[str, str] | None
    check: bool
    index: bool
    orders: NotRequired[list[Literal["ASC", "DESC"]]]
    type: NotRequired[str]
    definition: NotRequired[str | None]
    options: NotRequired[dict[str, str] | None]

@type_check_only
class _SequenceDict(TypedDict):
    table: str
    column: str
    name: NotRequired[str]

class BaseDatabaseIntrospection:
    data_types_reverse: Any
    connection: BaseDatabaseWrapper
    def __init__(self, connection: BaseDatabaseWrapper) -> None: ...
    def __del__(self) -> None: ...
    def get_field_type(self, data_type: str, description: FieldInfo) -> str: ...
    def identifier_converter(self, name: str) -> str: ...
    def table_names(self, cursor: CursorWrapper | None = None, include_views: bool = False) -> list[str]: ...
    def get_table_list(self, cursor: CursorWrapper) -> list[TableInfo]: ...
    def get_table_description(self, cursor: CursorWrapper, table_name: str) -> list[Any]: ...
    def get_migratable_models(self) -> Iterable[type[Model]]: ...
    def django_table_names(self, only_existing: bool = False, include_views: bool = True) -> list[str]: ...
    def installed_models(self, tables: list[str]) -> set[type[Model]]: ...
    def sequence_list(self) -> list[dict[str, str]]: ...
    def get_sequences(
        self, cursor: CursorWrapper, table_name: str, table_fields: Iterable[Field] = ()
    ) -> list[_SequenceDict]: ...
    def get_relations(self, cursor: CursorWrapper, table_name: str) -> dict[str, tuple[str, str]]: ...
    def get_primary_key_column(self, cursor: CursorWrapper, table_name: str) -> str | None: ...
    def get_primary_key_columns(self, cursor: CursorWrapper, table_name: str) -> list[str] | None: ...
    def get_constraints(self, cursor: CursorWrapper, table_name: str) -> dict[str, _ConstraintDict]: ...
