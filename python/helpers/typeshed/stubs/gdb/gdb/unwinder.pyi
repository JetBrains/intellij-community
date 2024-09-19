import gdb
from gdb import Frame, UnwindInfo

class Unwinder:
    name: str
    enabled: bool

    def __init__(self, name: str) -> None: ...
    def __call__(self, pending_frame: Frame) -> UnwindInfo | None: ...

def register_unwinder(locus: gdb.Objfile | gdb.Progspace | None, unwinder: Unwinder, replace: bool = ...) -> None: ...
