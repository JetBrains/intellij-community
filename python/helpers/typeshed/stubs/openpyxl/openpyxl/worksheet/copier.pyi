from typing import Any

class WorksheetCopy:
    source: Any
    target: Any
    def __init__(self, source_worksheet, target_worksheet) -> None: ...
    def copy_worksheet(self) -> None: ...
