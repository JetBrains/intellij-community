# Stubs for pickle

from typing import Any, IO, Union, Tuple, Callable, Optional, Iterator
# Imports used in type comments only.
from typing import Mapping  # noqa

HIGHEST_PROTOCOL = ...  # type: int
DEFAULT_PROTOCOL = ...  # type: int


def dump(obj: Any, file: IO[bytes], protocol: int = None, *,
         fix_imports: bool = ...) -> None: ...


def dumps(obj: Any, protocol: int = ..., *,
          fix_imports: bool = ...) -> bytes: ...


def loads(bytes_object: bytes, *, fix_imports: bool = ...,
          encoding: str = ..., errors: str = ...) -> Any: ...


def load(file: IO[bytes], *, fix_imports: bool = ..., encoding: str = ...,
         errors: str = ...) -> Any: ...


class PickleError(Exception):
    pass


class PicklingError(PickleError):
    pass


class UnpicklingError(PickleError):
    pass


_reducedtype = Union[str,
                     Tuple[Callable[..., Any], Tuple],
                     Tuple[Callable[..., Any], Tuple, Any],
                     Tuple[Callable[..., Any], Tuple, Any,
                           Optional[Iterator]],
                     Tuple[Callable[..., Any], Tuple, Any,
                           Optional[Iterator], Optional[Iterator]]]


class Pickler:
    dispatch_table = ...  # type: Mapping[type, Callable[[Any], _reducedtype]]

    def __init__(self, file: IO[bytes], protocol: int = None, *,
                 fix_imports: bool = ...) -> None: ...

    def dump(self, obj: Any) -> None: ...

    def persistent_id(self, obj: Any) -> Any: ...


class Unpickler:
    def __init__(self, file: IO[bytes], *, fix_imports: bool = ...,
                 encoding: str = ..., errors: str = ...) -> None: ...

    def load(self) -> Any: ...

    def persistent_load(self, pid: Any) -> Any: ...

    def find_class(self, module: str, name: str) -> Any: ...
