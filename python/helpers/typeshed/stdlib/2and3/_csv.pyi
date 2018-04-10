import sys

from typing import Any, Iterable, Iterator, List, Optional, Sequence

QUOTE_ALL = ...  # type: int
QUOTE_MINIMAL = ...  # type: int
QUOTE_NONE = ...  # type: int
QUOTE_NONNUMERIC = ...  # type: int

class Error(Exception): ...

class Dialect:
    delimiter = ...  # type: str
    quotechar = ...  # type: Optional[str]
    escapechar = ...  # type: Optional[str]
    doublequote = ...  # type: bool
    skipinitialspace = ...  # type: bool
    lineterminator = ...  # type: str
    quoting = ...  # type: int
    strict = ...  # type: int
    def __init__(self) -> None: ...

class _reader(Iterator[List[str]]):
    dialect = ...  # type: Dialect
    line_num = ...  # type: int

class _writer:
    dialect = ...  # type: Dialect

    if sys.version_info >= (3, 5):
        def writerow(self, row: Iterable[Any]) -> None: ...
        def writerows(self, rows: Iterable[Iterable[Any]]) -> None: ...
    else:
        def writerow(self, row: Sequence[Any]) -> None: ...
        def writerows(self, rows: Iterable[Sequence[Any]]) -> None: ...


# TODO: precise type
def writer(csvfile: Any, dialect: Any = ..., **fmtparams: Any) -> _writer: ...
def reader(csvfile: Iterable[str], dialect: Any = ..., **fmtparams: Any) -> _reader: ...
def register_dialect(name: str, dialect: Any = ..., **fmtparams: Any) -> None: ...
def unregister_dialect(name: str) -> None: ...
def get_dialect(name: str) -> Dialect: ...
def list_dialects() -> List[str]: ...
def field_size_limit(new_limit: int = ...) -> int: ...
