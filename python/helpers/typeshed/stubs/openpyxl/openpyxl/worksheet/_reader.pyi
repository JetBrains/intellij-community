from collections.abc import Generator
from typing import Any

CELL_TAG: Any
VALUE_TAG: Any
FORMULA_TAG: Any
MERGE_TAG: Any
INLINE_STRING: Any
COL_TAG: Any
ROW_TAG: Any
CF_TAG: Any
LEGACY_TAG: Any
PROT_TAG: Any
EXT_TAG: Any
HYPERLINK_TAG: Any
TABLE_TAG: Any
PRINT_TAG: Any
MARGINS_TAG: Any
PAGE_TAG: Any
HEADER_TAG: Any
FILTER_TAG: Any
VALIDATION_TAG: Any
PROPERTIES_TAG: Any
VIEWS_TAG: Any
FORMAT_TAG: Any
ROW_BREAK_TAG: Any
COL_BREAK_TAG: Any
SCENARIOS_TAG: Any
DATA_TAG: Any
DIMENSION_TAG: Any
CUSTOM_VIEWS_TAG: Any

class WorkSheetParser:
    min_row: Any
    epoch: Any
    source: Any
    shared_strings: Any
    data_only: Any
    shared_formulae: Any
    array_formulae: Any
    row_counter: int
    tables: Any
    date_formats: Any
    timedelta_formats: Any
    row_dimensions: Any
    column_dimensions: Any
    number_formats: Any
    keep_vba: bool
    hyperlinks: Any
    formatting: Any
    legacy_drawing: Any
    merged_cells: Any
    row_breaks: Any
    col_breaks: Any
    def __init__(
        self, src, shared_strings, data_only: bool = ..., epoch=..., date_formats=..., timedelta_formats=...
    ) -> None: ...
    def parse(self) -> Generator[Any, None, None]: ...
    def parse_dimensions(self): ...
    col_counter: Any
    def parse_cell(self, element): ...
    def parse_formula(self, element): ...
    def parse_column_dimensions(self, col) -> None: ...
    def parse_row(self, row): ...
    def parse_formatting(self, element) -> None: ...
    protection: Any
    def parse_sheet_protection(self, element) -> None: ...
    def parse_extensions(self, element) -> None: ...
    def parse_legacy(self, element) -> None: ...
    def parse_row_breaks(self, element) -> None: ...
    def parse_col_breaks(self, element) -> None: ...
    def parse_custom_views(self, element) -> None: ...

class WorksheetReader:
    ws: Any
    parser: Any
    tables: Any
    def __init__(self, ws, xml_source, shared_strings, data_only) -> None: ...
    def bind_cells(self) -> None: ...
    def bind_formatting(self) -> None: ...
    def bind_tables(self) -> None: ...
    def bind_merged_cells(self) -> None: ...
    def bind_hyperlinks(self) -> None: ...
    def normalize_merged_cell_link(self, coord): ...
    def bind_col_dimensions(self) -> None: ...
    def bind_row_dimensions(self) -> None: ...
    def bind_properties(self) -> None: ...
    def bind_all(self) -> None: ...
