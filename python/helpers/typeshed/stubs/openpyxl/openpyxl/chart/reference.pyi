from _typeshed import Incomplete, Unused
from collections.abc import Generator
from typing import overload
from typing_extensions import Literal

from openpyxl.descriptors import Strict
from openpyxl.descriptors.base import MinMax, String, _ConvertibleToInt

class DummyWorksheet:
    title: str
    def __init__(self, title: str) -> None: ...

class Reference(Strict):
    min_row: MinMax[int, Literal[False]]
    max_row: MinMax[int, Literal[False]]
    min_col: MinMax[int, Literal[False]]
    max_col: MinMax[int, Literal[False]]
    range_string: String[Literal[True]]
    worksheet: Incomplete | None
    @overload
    def __init__(
        self,
        *,
        worksheet: Unused = None,
        min_col: Unused = None,
        min_row: Unused = None,
        max_col: Unused = None,
        max_row: Unused = None,
        range_string: str,
    ) -> None: ...
    @overload
    def __init__(
        self,
        worksheet: Incomplete | None,
        min_col: _ConvertibleToInt,
        min_row: _ConvertibleToInt,
        max_col: _ConvertibleToInt | None = None,
        max_row: _ConvertibleToInt | None = None,
        range_string: str | None = None,
    ) -> None: ...
    def __len__(self) -> int: ...
    def __eq__(self, other): ...
    @property
    def rows(self) -> Generator[Reference, None, None]: ...
    @property
    def cols(self) -> Generator[Reference, None, None]: ...
    def pop(self): ...
    @property
    def sheetname(self) -> str: ...
