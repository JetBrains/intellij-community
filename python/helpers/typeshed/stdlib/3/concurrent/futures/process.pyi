from typing import Optional, Any
from ._base import Future, Executor

EXTRA_QUEUED_CALLS = ...  # type: Any

class BrokenProcessPool(RuntimeError): ...

class ProcessPoolExecutor(Executor):
    def __init__(self, max_workers: Optional[int] = ...) -> None: ...
