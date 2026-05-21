from collections.abc import Iterator
from typing import Any, Literal, NamedTuple

from django.db.migrations.state import ModelState, ProjectState
from django.db.models import Field, Model

COMPILED_REGEX_TYPE: Any
RECURSIVE_RELATIONSHIP_CONSTANT: str

class RegexObject:
    pattern: str
    flags: int
    def __init__(self, obj: Any) -> None: ...

def get_migration_name_timestamp() -> str: ...
def resolve_relation(
    model: str | type[Model], app_label: str | None = None, model_name: str | None = None
) -> tuple[str, str]: ...

class FieldReference(NamedTuple):
    to: Any
    through: Any

def field_references(
    model_tuple: tuple[str, str],
    field: Field,
    reference_model_tuple: tuple[str, str],
    reference_field_name: str | None = None,
    reference_field: Field | None = None,
) -> Literal[False] | FieldReference: ...
def get_references(
    state: ProjectState,
    model_tuple: tuple[str, str],
    field_tuple: tuple[()] | tuple[str, Field] = (),
) -> Iterator[tuple[ModelState, str, Field, FieldReference]]: ...
def field_is_referenced(state: ProjectState, model_tuple: tuple[str, str], field_tuple: tuple[str, Field]) -> bool: ...
