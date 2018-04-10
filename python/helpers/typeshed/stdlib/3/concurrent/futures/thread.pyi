from typing import Optional
from ._base import Executor, Future
import sys

class ThreadPoolExecutor(Executor):
    if sys.version_info >= (3, 6):
        def __init__(self, max_workers: Optional[int] = ...,
                     thread_name_prefix: str = ...) -> None: ...
    else:
        def __init__(self, max_workers: Optional[int] = ...) -> None: ...
