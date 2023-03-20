from collections.abc import Generator
from typing import Any

from openpyxl.descriptors import Strict

class DummyWorksheet:
    title: Any
    def __init__(self, title) -> None: ...

class Reference(Strict):
    min_row: Any
    max_row: Any
    min_col: Any
    max_col: Any
    range_string: Any
    worksheet: Any
    def __init__(
        self,
        worksheet: Any | None = ...,
        min_col: Any | None = ...,
        min_row: Any | None = ...,
        max_col: Any | None = ...,
        max_row: Any | None = ...,
        range_string: Any | None = ...,
    ) -> None: ...
    def __len__(self): ...
    def __eq__(self, other): ...
    @property
    def rows(self) -> Generator[Any, None, None]: ...
    @property
    def cols(self) -> Generator[Any, None, None]: ...
    def pop(self): ...
    @property
    def sheetname(self): ...
