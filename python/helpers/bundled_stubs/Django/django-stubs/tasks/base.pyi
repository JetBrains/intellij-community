from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Generic, TypeVar, overload

from django.db.models.enums import TextChoices as TextChoices
from django.utils.module_loading import import_string as import_string
from django.utils.translation import pgettext_lazy as pgettext_lazy
from typing_extensions import ParamSpec

from .backends.base import BaseTaskBackend
from .exceptions import TaskResultMismatch as TaskResultMismatch

DEFAULT_TASK_BACKEND_ALIAS: str
DEFAULT_TASK_PRIORITY: int
DEFAULT_TASK_QUEUE_NAME: str
TASK_MAX_PRIORITY: int
TASK_MIN_PRIORITY: int
TASK_REFRESH_ATTRS: set[str]

class TaskResultStatus(TextChoices):
    READY = ...
    RUNNING = ...
    FAILED = ...
    SUCCESSFUL = ...

_P = ParamSpec("_P")
_R = TypeVar("_R")

@dataclass(kw_only=True)
class Task(Generic[_P, _R]):
    priority: int
    func: Callable[_P, _R]
    backend: str
    queue_name: str
    run_after: datetime | None
    takes_context: bool = ...
    def __post_init__(self) -> None: ...
    @property
    def name(self) -> str: ...
    def using(
        self,
        *,
        priority: int | None = None,
        queue_name: str | None = None,
        run_after: datetime | None = None,
        backend: str | None = None,
    ) -> Task[_P, _R]: ...
    def enqueue(self, *args: _P.args, **kwargs: _P.kwargs) -> TaskResult[_P, _R]: ...
    async def aenqueue(self, *args: _P.args, **kwargs: _P.kwargs) -> TaskResult[_P, _R]: ...
    def get_result(self, result_id: str) -> TaskResult[_P, _R]: ...
    async def aget_result(self, result_id: str) -> TaskResult[_P, _R]: ...
    def call(self, *args: _P.args, **kwargs: _P.kwargs) -> _R: ...
    async def acall(self, *args: _P.args, **kwargs: _P.kwargs) -> _R: ...
    def get_backend(self) -> BaseTaskBackend: ...
    @property
    def module_path(self) -> str: ...

@overload
def task(
    function: Callable[_P, _R],
    *,
    priority: int | None = None,
    queue_name: str | None = None,
    backend: str | None = None,
    takes_context: bool = ...,
) -> Task[_P, _R]: ...
@overload
def task(
    *,
    priority: int | None = None,
    queue_name: str | None = None,
    backend: str | None = None,
    takes_context: bool = ...,
) -> Callable[[Callable[_P, _R]], Task[_P, _R]]: ...

@dataclass(kw_only=True)
class TaskError:
    exception_class_path: str
    traceback: str
    @property
    def exception_class(self) -> type[BaseException]: ...

@dataclass(kw_only=True)
class TaskResult(Generic[_P, _R]):
    task: Task[_P, _R]
    id: str
    status: TaskResultStatus
    enqueued_at: datetime | None
    started_at: datetime | None
    finished_at: datetime | None
    last_attempted_at: datetime | None
    args: list[Any]
    kwargs: dict[str, Any]
    backend: str
    errors: list[TaskError]
    worker_ids: list[str]
    def __post_init__(self) -> None: ...
    @property
    def return_value(self) -> _R: ...
    @property
    def is_finished(self) -> bool: ...
    @property
    def attempts(self) -> int: ...
    def refresh(self) -> None: ...
    async def arefresh(self) -> None: ...

@dataclass(kw_only=True)
class TaskContext(Generic[_P, _R]):
    task_result: TaskResult[_P, _R]
    @property
    def attempt(self) -> int: ...
