# Stubs-only module with type aliases for ExifRead.

from typing import Any, Protocol
from typing_extensions import Literal, TypeAlias

# The second item of the value tuple - if it exists - can be a variety of types,
# including a callable or another dict.
TagDict: TypeAlias = dict[int, tuple[str] | tuple[str, Any]]

class Reader(Protocol):
    def __iter__(self) -> bytes: ...
    def read(self, __size: int) -> bytes: ...
    def tell(self) -> int: ...
    def seek(self, __offset: int, __whence: Literal[0, 1] = ...) -> object: ...
