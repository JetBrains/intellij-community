from typing import Any

from openpyxl.descriptors import String
from openpyxl.descriptors.serialisable import Serialisable

BUILTIN_FORMATS: Any
BUILTIN_FORMATS_MAX_SIZE: int
BUILTIN_FORMATS_REVERSE: Any
FORMAT_GENERAL: Any
FORMAT_TEXT: Any
FORMAT_NUMBER: Any
FORMAT_NUMBER_00: Any
FORMAT_NUMBER_COMMA_SEPARATED1: Any
FORMAT_NUMBER_COMMA_SEPARATED2: str
FORMAT_PERCENTAGE: Any
FORMAT_PERCENTAGE_00: Any
FORMAT_DATE_YYYYMMDD2: str
FORMAT_DATE_YYMMDD: str
FORMAT_DATE_DDMMYY: str
FORMAT_DATE_DMYSLASH: str
FORMAT_DATE_DMYMINUS: str
FORMAT_DATE_DMMINUS: str
FORMAT_DATE_MYMINUS: str
FORMAT_DATE_XLSX14: Any
FORMAT_DATE_XLSX15: Any
FORMAT_DATE_XLSX16: Any
FORMAT_DATE_XLSX17: Any
FORMAT_DATE_XLSX22: Any
FORMAT_DATE_DATETIME: str
FORMAT_DATE_TIME1: Any
FORMAT_DATE_TIME2: Any
FORMAT_DATE_TIME3: Any
FORMAT_DATE_TIME4: Any
FORMAT_DATE_TIME5: Any
FORMAT_DATE_TIME6: Any
FORMAT_DATE_TIME7: str
FORMAT_DATE_TIME8: str
FORMAT_DATE_TIMEDELTA: str
FORMAT_DATE_YYMMDDSLASH: str
FORMAT_CURRENCY_USD_SIMPLE: str
FORMAT_CURRENCY_USD: str
FORMAT_CURRENCY_EUR_SIMPLE: str
COLORS: str
LITERAL_GROUP: str
LOCALE_GROUP: str
STRIP_RE: Any
TIMEDELTA_RE: Any

def is_date_format(fmt): ...
def is_timedelta_format(fmt): ...
def is_datetime(fmt): ...
def is_builtin(fmt): ...
def builtin_format_code(index): ...
def builtin_format_id(fmt): ...

class NumberFormatDescriptor(String):
    def __set__(self, instance, value) -> None: ...

class NumberFormat(Serialisable):  # type: ignore[misc]
    numFmtId: Any
    formatCode: Any
    def __init__(self, numFmtId: Any | None = ..., formatCode: Any | None = ...) -> None: ...

class NumberFormatList(Serialisable):  # type: ignore[misc]
    # Overwritten by property below
    # count: Integer
    numFmt: Any
    __elements__: Any
    __attrs__: Any
    def __init__(self, count: Any | None = ..., numFmt=...) -> None: ...
    @property
    def count(self): ...
    def __getitem__(self, idx): ...
