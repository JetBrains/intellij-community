
import sys

from typing import List, Optional, Union
from types import FrameType
from . import tasks

if sys.version_info >= (3, 6):
    from builtins import _PathLike
    _PathType = Union[bytes, str, _PathLike]
else:
    _PathType = Union[bytes, str]

def _task_repr_info(task: tasks.Task) -> List[str]: ...  # undocumented
def _task_get_stack(task: tasks.Task, limit: Optional[int]) -> List[FrameType]: ...  # undocumented
def _task_print_stack(task: tasks.Task, limit: Optional[int], file: _PathType): ...  # undocumented
