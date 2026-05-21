from typing import Any

from django.tasks.base import Task, TaskResult
from typing_extensions import override

from .base import BaseTaskBackend

class DummyBackend(BaseTaskBackend):
    results: list[TaskResult]
    def __init__(self, alias: str, params: dict[str, Any]) -> None: ...
    @override
    def enqueue(self, task: Task, args: list[Any], kwargs: dict[str, Any]) -> TaskResult: ...
    @override
    def get_result(self, result_id: str) -> TaskResult: ...
    @override
    async def aget_result(self, result_id: str) -> TaskResult: ...
    def clear(self) -> None: ...
