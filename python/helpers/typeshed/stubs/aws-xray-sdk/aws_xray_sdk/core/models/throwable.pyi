from _typeshed import Incomplete
from logging import Logger
from traceback import StackSummary
from typing import TypedDict, type_check_only

@type_check_only
class _StackInfo(TypedDict):
    path: str
    line: int
    label: str

log: Logger

class Throwable:
    id: str
    message: str
    type: str
    remote: bool
    stack: list[_StackInfo] | None
    def __init__(self, exception: Exception, stack: StackSummary, remote: bool = False) -> None: ...
    def to_dict(self) -> dict[str, Incomplete]: ...
