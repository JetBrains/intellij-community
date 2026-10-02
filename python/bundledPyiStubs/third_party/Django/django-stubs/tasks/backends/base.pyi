from abc import ABCMeta, abstractmethod
from typing import Any

from django.tasks.base import Task, TaskResult
from typing_extensions import ParamSpec, TypeVar

_P = ParamSpec("_P")
_R = TypeVar("_R")

class BaseTaskBackend(metaclass=ABCMeta):
    task_class: type[Task[..., Any]]
    supports_defer: bool
    supports_async_task: bool
    supports_get_result: bool
    supports_priority: bool
    alias: str
    queues: set[str]
    options: dict[str, Any]
    def __init__(self, alias: str, params: dict[str, Any]) -> None: ...
    def validate_task(self, task: Task[..., Any]) -> None: ...
    @abstractmethod
    def enqueue(self, task: Task[_P, _R], args: list[Any], kwargs: dict[str, Any]) -> TaskResult[_P, _R]: ...
    async def aenqueue(self, task: Task[_P, _R], args: list[Any], kwargs: dict[str, Any]) -> TaskResult[_P, _R]: ...
    def get_result(self, result_id: str) -> TaskResult[..., Any]: ...
    async def aget_result(self, result_id: str) -> TaskResult[..., Any]: ...
    def check(self, **kwargs: Any) -> list[Any]: ...
