import sys
from ctypes import Structure, Union
from typing import Any

LOCK_SH: int
LOCK_NB: int
LOCK_EX: int

if sys.platform == "win32":
    ULONG_PTR: Any
    PVOID: Any
    class _OFFSET(Structure): ...
    class _OFFSET_UNION(Union): ...
    class OVERLAPPED(Structure): ...

def lock(f: Any, flags: int) -> bool: ...
def unlock(f: Any) -> bool: ...

__all__ = ("LOCK_EX", "LOCK_NB", "LOCK_SH", "lock", "unlock")
