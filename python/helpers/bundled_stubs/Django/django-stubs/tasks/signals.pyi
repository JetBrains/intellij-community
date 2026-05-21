from logging import Logger
from typing import Any

from django.dispatch import Signal as Signal
from django.tasks.backends.base import BaseTaskBackend
from django.tasks.base import TaskResult

from .base import TaskResultStatus as TaskResultStatus

logger: Logger

task_enqueued: Signal
task_finished: Signal
task_started: Signal

def clear_tasks_handlers(*, setting: str, **kwargs: Any) -> None: ...
def log_task_enqueued(sender: type[BaseTaskBackend], task_result: TaskResult, **kwargs: Any) -> None: ...
def log_task_started(sender: type[BaseTaskBackend], task_result: TaskResult, **kwargs: Any) -> None: ...
def log_task_finished(sender: type[BaseTaskBackend], task_result: TaskResult, **kwargs: Any) -> None: ...
