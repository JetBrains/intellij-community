from collections.abc import Sequence
from typing import Any, Protocol, type_check_only

from typing_extensions import override

@type_check_only
class _QuoteCallable(Protocol):
    def __call__(self, column: str, /) -> str: ...

class Reference:
    def references_table(self, table: Any) -> bool: ...
    def references_column(self, table: Any, column: Any) -> bool: ...
    def references_index(self, table: Any, index: Any) -> bool: ...
    def rename_table_references(self, old_table: Any, new_table: Any) -> None: ...
    def rename_column_references(self, table: Any, old_column: Any, new_column: Any) -> None: ...

class Table(Reference):
    table: str
    quote_name: _QuoteCallable
    def __init__(self, table: str, quote_name: _QuoteCallable) -> None: ...
    @override
    def references_table(self, table: str) -> bool: ...
    @override
    def references_index(self, table: Any, index: Any) -> bool: ...
    @override
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...

class TableColumns(Table):
    table: str
    columns: list[str]
    def __init__(self, table: str, columns: list[str]) -> None: ...
    @override
    def references_column(self, table: str, column: str) -> bool: ...
    @override
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...

class Columns(TableColumns):
    quote_name: _QuoteCallable
    col_suffixes: Sequence[str]
    def __init__(
        self, table: str, columns: list[str], quote_name: _QuoteCallable, col_suffixes: Sequence[str] = ()
    ) -> None: ...

@type_check_only
class _NameCallable(Protocol):
    def __call__(self, table: str, columns: list[str], suffix: str, /) -> str: ...

class IndexName(TableColumns):
    suffix: str
    create_index_name: _NameCallable
    def __init__(self, table: str, columns: list[str], suffix: str, create_index_name: _NameCallable) -> None: ...

class IndexColumns(Columns):
    opclasses: Any
    def __init__(
        self, table: Any, columns: Any, quote_name: Any, col_suffixes: Any = (), opclasses: Any = ()
    ) -> None: ...

class ForeignKeyName(TableColumns):
    to_reference: TableColumns
    suffix_template: str
    create_fk_name: _NameCallable
    def __init__(
        self,
        from_table: str,
        from_columns: list[str],
        to_table: str,
        to_columns: list[str],
        suffix_template: str,
        create_fk_name: _NameCallable,
    ) -> None: ...
    @override
    def references_table(self, table: str) -> bool: ...
    @override
    def references_column(self, table: str, column: str) -> bool: ...
    @override
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...
    @override
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...

class Expressions(TableColumns):
    compiler: Any
    expressions: Any
    quote_value: Any
    def __init__(self, table: str, expressions: Any, compiler: Any, quote_value: Any) -> None: ...

class Statement(Reference):
    template: str
    parts: dict[str, Table]
    def __init__(self, template: str, **parts: Any) -> None: ...
    @override
    def references_table(self, table: str) -> bool: ...
    @override
    def references_column(self, table: str, column: str) -> bool: ...
    @override
    def references_index(self, table: Any, index: Any) -> bool: ...
    @override
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...
    @override
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...
