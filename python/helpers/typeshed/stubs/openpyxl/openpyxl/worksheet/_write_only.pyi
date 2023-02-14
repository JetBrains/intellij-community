from typing import Any

from openpyxl.workbook.child import _WorkbookChild

class WriteOnlyWorksheet(_WorkbookChild):
    mime_type: Any
    add_chart: Any
    add_image: Any
    add_table: Any
    @property
    def tables(self): ...
    @property
    def print_titles(self): ...
    print_title_cols: Any
    print_title_rows: Any
    freeze_panes: Any
    print_area: Any
    @property
    def sheet_view(self): ...
    def __init__(self, parent, title) -> None: ...
    @property
    def closed(self): ...
    def close(self) -> None: ...
    def append(self, row) -> None: ...
