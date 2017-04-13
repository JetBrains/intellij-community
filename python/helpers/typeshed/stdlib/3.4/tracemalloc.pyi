# Stubs for tracemalloc (Python 3.4+)

import sys
from typing import Any, List, Optional, Sequence, Tuple, Union

def clear_traces() -> None: ...
def get_object_traceback(obj: object) -> Optional[Traceback]: ...
def get_traceback_limit() -> int: ...
def get_traced_memory() -> Tuple[int, int]: ...
def get_tracemalloc_memory() -> int: ...
def is_tracing() -> bool: ...
def start(nframe: int = ...) -> None: ...
def stop() -> None: ...
def take_snapshot() -> Snapshot: ...

if sys.version_info >= (3, 6):
    class DomainFilter:
        inclusive = ...  # type: bool
        domain = ...  # type: int
        def __init__(self, inclusive: bool, domain: int) -> None: ...

class Filter:
    if sys.version_info >= (3, 6):
        domain = ...  # type: Optional[int]
    inclusive = ...  # type: bool
    lineno = ...  # type: Optional[int]
    filename_pattern = ...  # type: str
    all_frames = ...  # type: bool
    def __init__(self, inclusive: bool, filename_pattern: str, lineno: Optional[int] = ..., all_frames: bool = ..., domain: Optional[int] = ...) -> None: ...

class Frame:
    filename = ...  # type: str
    lineno = ...  # type: int

class Snapshot:
    def compare_to(self, old_snapshot: Snapshot, key_type: str, cumulative: bool = ...) -> List[StatisticDiff]: ...
    def dump(self, filename: str) -> None: ...
    if sys.version_info >= (3, 6):
        def filter_traces(self, filters: Sequence[Union[DomainFilter, Filter]]) -> Snapshot: ...
    else:
        def filter_traces(self, filters: Sequence[Filter]) -> Snapshot: ...
    @classmethod
    def load(cls, filename: str) -> Snapshot: ...
    def statistics(self, key_type: str, cumulative: bool = ...) -> List[Statistic]: ...
    traceback_limit = ...  # type: int
    traces = ...  # type: Sequence[Trace]

class Statistic:
    count = ...  # type: int
    size = ...  # type: int
    traceback = ...  # type: Traceback

class StatisticDiff:
    count = ...  # type: int
    count_diff = ...  # type: int
    size = ...  # type: int
    size_diff = ...  # type: int
    traceback = ...  # type: Traceback

class Trace:
    size = ...  # type: int
    traceback = ...  # type: Traceback

class Traceback(Sequence[Frame]):
    def format(self, limit: Optional[int] = ...) -> List[str]: ...
