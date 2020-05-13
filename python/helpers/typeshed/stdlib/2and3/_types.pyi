# These types are for typeshed-only objects that don't exist at runtime

from typing import type_check_only, Protocol, Union

@type_check_only
class HasFileno(Protocol):
    def fileno(self) -> int: ...

FileDescriptor = int
FileDescriptorLike = Union[int, HasFileno]
