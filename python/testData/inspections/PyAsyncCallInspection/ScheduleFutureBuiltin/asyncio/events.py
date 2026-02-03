from .tasks import Task
from .futures import Future


class AbstractEventLoop:
    def create_task(self, coro) -> Task:
        pass

    def run_in_executor(self, executor, func, *args) -> Future:
        pass