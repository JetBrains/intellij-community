from collections.abc import Sequence
from typing import Any, Protocol, type_check_only

@type_check_only
class _QuoteCallable(Protocol):
    """Get rid of `cannot assign to method`"""

    def __call__(self, column: str, /) -> str: ...

class Reference:
    def references_table(self, table: Any) -> bool: ...
    def references_column(self, table: Any, column: Any) -> bool: ...
    def rename_table_references(self, old_table: Any, new_table: Any) -> None: ...
    def rename_column_references(self, table: Any, old_column: Any, new_column: Any) -> None: ...

class Table(Reference):
    table: str
    quote_name: _QuoteCallable
    def __init__(self, table: str, quote_name: _QuoteCallable) -> None: ...
    def references_table(self, table: str) -> bool: ...
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...

class TableColumns(Table):
    table: str
    columns: list[str]
    def __init__(self, table: str, columns: list[str]) -> None: ...
    def references_column(self, table: str, column: str) -> bool: ...
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...

class Columns(TableColumns):
    columns: list[str]
    table: str
    quote_name: _QuoteCallable
    col_suffixes: Sequence[str]
    def __init__(
        self, table: str, columns: list[str], quote_name: _QuoteCallable, col_suffixes: Sequence[str] = ()
    ) -> None: ...

@type_check_only
class _NameCallable(Protocol):
    """Get rid of `cannot assign to method`"""

    def __call__(self, table: str, columns: list[str], suffix: str, /) -> str: ...

class IndexName(TableColumns):
    columns: list[str]
    table: str
    suffix: str
    create_index_name: _NameCallable
    def __init__(self, table: str, columns: list[str], suffix: str, create_index_name: _NameCallable) -> None: ...

class IndexColumns(Columns):
    opclasses: Any
    def __init__(
        self, table: Any, columns: Any, quote_name: Any, col_suffixes: Any = (), opclasses: Any = ()
    ) -> None: ...

class ForeignKeyName(TableColumns):
    columns: list[str]
    table: str
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
    def references_table(self, table: str) -> bool: ...
    def references_column(self, table: str, column: str) -> bool: ...
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...

class Statement(Reference):
    template: str
    parts: dict[str, Table]
    def __init__(self, template: str, **parts: Any) -> None: ...
    def references_table(self, table: str) -> bool: ...
    def references_column(self, table: str, column: str) -> bool: ...
    def rename_table_references(self, old_table: str, new_table: str) -> None: ...
    def rename_column_references(self, table: str, old_column: str, new_column: str) -> None: ...
