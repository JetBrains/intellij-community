from logging import Logger
from typing import Any

from django.tasks.base import Task, TaskResult
from typing_extensions import ParamSpec, TypeVar, override

from .base import BaseTaskBackend

_P = ParamSpec("_P")
_R = TypeVar("_R")

logger: Logger

class ImmediateBackend(BaseTaskBackend):
    worker_id: str
    def __init__(self, alias: str, params: dict[str, Any]) -> None: ...
    @override
    def enqueue(self, task: Task[_P, _R], args: list[Any], kwargs: dict[str, Any]) -> TaskResult[_P, _R]: ...
