from collections import OrderedDict
import sys
from typing import Any, Dict, Iterable, Iterator, List, Optional, Sequence, Union

from _csv import (_reader,
                  _writer,
                  reader as reader,
                  writer as writer,
                  register_dialect as register_dialect,
                  unregister_dialect as unregister_dialect,
                  get_dialect as get_dialect,
                  list_dialects as list_dialects,
                  field_size_limit as field_size_limit,
                  QUOTE_ALL as QUOTE_ALL,
                  QUOTE_MINIMAL as QUOTE_MINIMAL,
                  QUOTE_NONE as QUOTE_NONE,
                  QUOTE_NONNUMERIC as QUOTE_NONNUMERIC,
                  Error as Error,
                  )

_Dialect = Union[str, Dialect]
_DictRow = Dict[str, Any]

class Dialect(object):
    delimiter = ...  # type: str
    quotechar = ...  # type: Optional[str]
    escapechar = ...  # type: Optional[str]
    doublequote = ...  # type: bool
    skipinitialspace = ...  # type: bool
    lineterminator = ...  # type: str
    quoting = ...  # type: int
    def __init__(self) -> None: ...

class excel(Dialect):
    delimiter = ...  # type: str
    quotechar = ...  # type: str
    doublequote = ...  # type: bool
    skipinitialspace = ...  # type: bool
    lineterminator = ...  # type: str
    quoting = ...  # type: int

class excel_tab(excel):
    delimiter = ...  # type: str

if sys.version_info >= (3,):
    class unix_dialect(Dialect):
        delimiter = ...  # type: str
        quotechar = ...  # type: str
        doublequote = ...  # type: bool
        skipinitialspace = ...  # type: bool
        lineterminator = ...  # type: str
        quoting = ...  # type: int

if sys.version_info >= (3, 6):
    _DRMapping = OrderedDict[str, str]
else:
    _DRMapping = Dict[str, str]


class DictReader(Iterator[_DRMapping]):
    restkey = ...  # type: Optional[str]
    restval = ...  # type: Optional[str]
    reader = ...  # type: _reader
    dialect = ...  # type: _Dialect
    line_num = ...  # type: int
    fieldnames = ...  # type: Sequence[str]
    def __init__(self, f: Iterable[str], fieldnames: Sequence[str] = ...,
                 restkey: Optional[str] = ..., restval: Optional[str] = ..., dialect: _Dialect = ...,
                 *args: Any, **kwds: Any) -> None: ...
    def __iter__(self) -> 'DictReader': ...
    if sys.version_info >= (3,):
        def __next__(self) -> _DRMapping: ...
    else:
        def next(self) -> _DRMapping: ...


class DictWriter(object):
    fieldnames = ...  # type: Sequence[str]
    restval = ...  # type: Optional[Any]
    extrasaction = ...  # type: str
    writer = ...  # type: _writer
    def __init__(self, f: Any, fieldnames: Sequence[str],
                 restval: Optional[Any] = ..., extrasaction: str = ..., dialect: _Dialect = ...,
                 *args: Any, **kwds: Any) -> None: ...
    def writeheader(self) -> None: ...
    def writerow(self, rowdict: _DictRow) -> None: ...
    def writerows(self, rowdicts: Iterable[_DictRow]) -> None: ...

class Sniffer(object):
    preferred = ...  # type: List[str]
    def __init__(self) -> None: ...
    def sniff(self, sample: str, delimiters: Optional[str] = ...) -> Dialect: ...
    def has_header(self, sample: str) -> bool: ...
