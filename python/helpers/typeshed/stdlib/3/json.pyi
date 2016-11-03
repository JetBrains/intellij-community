from typing import Any, IO, Iterator, Optional, Tuple, Callable, Dict, List, Union

class JSONDecodeError(ValueError):
    def dumps(self, obj: Any) -> str: ...
    def dump(self, obj: Any, fp: IO[str], *args: Any, **kwds: Any) -> None: ...
    def loads(self, s: str) -> Any: ...
    def load(self, fp: IO[str]) -> Any: ...

def dumps(obj: Any,
    skipkeys: bool = ...,
    ensure_ascii: bool = ...,
    check_circular: bool = ...,
    allow_nan: bool = ...,
    cls: Any = ...,
    indent: Union[None, int, str] = ...,
    separators: Optional[Tuple[str, str]] = ...,
    default: Optional[Callable[[Any], Any]] = ...,
    sort_keys: bool = ...,
    **kwds: Any) -> str: ...

def dump(obj: Any,
    fp: IO[str],
    skipkeys: bool = ...,
    ensure_ascii: bool = ...,
    check_circular: bool = ...,
    allow_nan: bool = ...,
    cls: Any = ...,
    indent: Union[None, int, str] = ...,
    separators: Optional[Tuple[str, str]] = ...,
    default: Optional[Callable[[Any], Any]] = ...,
    sort_keys: bool = ...,
    **kwds: Any) -> None: ...

def loads(s: str,
    encoding: Any = ..., # ignored and deprecated
    cls: Any = ...,
    object_hook: Callable[[Dict], Any] = ...,
    parse_float: Optional[Callable[[str], Any]] = ...,
    parse_int: Optional[Callable[[str], Any]] = ...,
    parse_constant: Optional[Callable[[str], Any]] = ...,
    object_pairs_hook: Optional[Callable[[List[Tuple[Any, Any]]], Any]] = ...,
    **kwds: Any) -> Any: ...

def load(fp: IO[str],
    cls: Any = ...,
    object_hook: Callable[[Dict], Any] = ...,
    parse_float: Optional[Callable[[str], Any]] = ...,
    parse_int: Optional[Callable[[str], Any]] = ...,
    parse_constant: Optional[Callable[[str], Any]] = ...,
    object_pairs_hook: Optional[Callable[[List[Tuple[Any, Any]]], Any]] = ...,
    **kwds: Any) -> Any: ...

class JSONEncoder(object):
    item_separator = ...  # type: str
    key_separator = ...  # type: str

    skipkeys = ...  # type: bool
    ensure_ascii = ...  # type: bool
    check_circular = ...  # type: bool
    allow_nan = ...  # type: bool
    sort_keys = ...  # type: bool
    indent = None  # type: int

    def __init__(self, skipkeys: bool=..., ensure_ascii: bool=...,
            check_circular: bool=..., allow_nan: bool=..., sort_keys: bool=...,
            indent: int=None, separators: Tuple[str, str]=None, default: Callable=None) -> None: ...

    def default(self, o: Any) -> Any: ...
    def encode(self, o: Any) -> str: ...
    def iterencode(self, o: Any, _one_shot: bool=False) -> Iterator[str]: ...

class JSONDecoder(object):

    object_hook = None  # type: Callable[[Dict[str, Any]], Any]
    parse_float = ...  # Callable[[str], Any]
    parse_int = ...  # Callable[[str], Any]
    parse_constant = ...  # Callable[[str], Any]
    strict = ...  # type: bool
    object_pairs_hook = None  # type: Callable[[List[Tuple[str, Any]]], Any]

    def __init__(self, object_hook: Callable[[Dict[str, Any]], Any]=None,
            parse_float: Callable[[str], Any]=None,
            parse_int: Callable[[str], Any]=None,
            parse_constant: Callable[[str], Any]=None,
            strict: bool=True,
            object_pairs_hook: Callable[[List[Tuple[str, Any]]], Any]=None) -> None: ...
    def decode(self, s: str) -> Any: ...
    def raw_decode(self, s: str) -> Tuple[Any, int]: ...
