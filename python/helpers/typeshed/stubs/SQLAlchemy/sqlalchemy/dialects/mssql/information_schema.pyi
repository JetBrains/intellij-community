from typing import Any

from ...sql import expression
from ...types import TypeDecorator

ischema: Any

class CoerceUnicode(TypeDecorator):
    impl: Any
    cache_ok: bool
    def process_bind_param(self, value, dialect): ...
    def bind_expression(self, bindvalue): ...

class _cast_on_2005(expression.ColumnElement[Any]):
    bindvalue: Any
    def __init__(self, bindvalue) -> None: ...

schemata: Any
tables: Any
columns: Any
mssql_temp_table_columns: Any
constraints: Any
column_constraints: Any
key_constraints: Any
ref_constraints: Any
views: Any
computed_columns: Any
sequences: Any

class IdentitySqlVariant(TypeDecorator):
    impl: Any
    cache_ok: bool
    def column_expression(self, colexpr): ...

identity_columns: Any
