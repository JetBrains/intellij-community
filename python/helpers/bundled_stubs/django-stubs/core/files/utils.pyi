from collections.abc import Iterator
from typing import IO, Any, AnyStr, Generic

from django.utils._os import _PathCompatible

def validate_file_name(name: _PathCompatible, allow_relative_path: bool = ...) -> _PathCompatible: ...

class FileProxyMixin(Generic[AnyStr]):
    file: IO[AnyStr] | None
    encoding: Any
    fileno: Any
    flush: Any
    isatty: Any
    newlines: Any
    read: Any
    readinto: Any
    readline: Any
    readlines: Any
    seek: Any
    tell: Any
    truncate: Any
    write: Any
    writelines: Any
    @property
    def closed(self) -> bool: ...
    def readable(self) -> bool: ...
    def writable(self) -> bool: ...
    def seekable(self) -> bool: ...
    def __iter__(self) -> Iterator[AnyStr]: ...
