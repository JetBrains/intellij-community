from django.utils.connection import BaseConnectionHandler

from .backends.base import BaseTaskBackend
from .base import DEFAULT_TASK_BACKEND_ALIAS as DEFAULT_TASK_BACKEND_ALIAS
from .base import DEFAULT_TASK_QUEUE_NAME as DEFAULT_TASK_QUEUE_NAME
from .base import Task as Task
from .base import TaskContext as TaskContext
from .base import TaskResult as TaskResult
from .base import TaskResultStatus as TaskResultStatus
from .base import task as task

__all__ = [
    "DEFAULT_TASK_BACKEND_ALIAS",
    "DEFAULT_TASK_QUEUE_NAME",
    "Task",
    "TaskContext",
    "TaskResult",
    "TaskResultStatus",
    "default_task_backend",
    "task",
    "task_backends",
]

class TaskBackendHandler(BaseConnectionHandler[BaseTaskBackend]): ...

task_backends: TaskBackendHandler
# Actually ConnectionProxy, but quacks exactly like BaseTaskBackend, it's not worth distinguishing the two.
default_task_backend: BaseTaskBackend
