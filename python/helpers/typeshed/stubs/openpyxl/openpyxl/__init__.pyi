from typing import Protocol
from typing_extensions import Literal, TypeAlias

from openpyxl.compat.numbers import NUMPY as NUMPY
from openpyxl.reader.excel import load_workbook as load_workbook
from openpyxl.workbook import Workbook as Workbook
from openpyxl.xml import DEFUSEDXML as DEFUSEDXML, LXML as LXML

from ._constants import (
    __author__ as __author__,
    __author_email__ as __author_email__,
    __license__ as __license__,
    __maintainer_email__ as __maintainer_email__,
    __url__ as __url__,
    __version__ as __version__,
)

DEBUG: bool
open = load_workbook

# Utility types reused elsewhere
_VisibilityType: TypeAlias = Literal["visible", "hidden", "veryHidden"]  # noqa: Y047

class _Decodable(Protocol):  # noqa: Y046
    def decode(self, __encoding: str) -> str: ...
