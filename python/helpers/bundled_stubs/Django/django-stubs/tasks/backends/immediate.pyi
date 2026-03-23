from logging import Logger
from typing import Any

from django.tasks.base import Task, TaskResult
from typing_extensions import override

from .base import BaseTaskBackend

logger: Logger

class ImmediateBackend(BaseTaskBackend):
    worker_id: str
    def __init__(self, alias: str, params: dict[str, Any]) -> None: ...
    @override
    def enqueue(self, task: Task, args: list[Any], kwargs: dict[str, Any]) -> TaskResult: ...
