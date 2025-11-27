import sys
from _typeshed import FileDescriptorOrPath, StrOrBytesPath

from ._common import sdiskusage

def pid_exists(pid: int) -> bool: ...
def wait_pid(
    pid: int,
    timeout: float | None = None,
    proc_name: str | None = None,
    _waitpid=...,
    _timer=...,
    _min=...,
    _sleep=...,
    _pid_exists=...,
): ...

if sys.platform == "darwin":
    def disk_usage(path: StrOrBytesPath) -> sdiskusage: ...

else:
    def disk_usage(path: FileDescriptorOrPath) -> sdiskusage: ...

def get_terminal_map() -> dict[int, str]: ...

__all__ = ["pid_exists", "wait_pid", "disk_usage", "get_terminal_map"]
