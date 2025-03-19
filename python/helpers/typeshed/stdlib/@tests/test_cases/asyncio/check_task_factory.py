import asyncio
import sys


def get_set(loop: asyncio.BaseEventLoop) -> None:
    loop.set_task_factory(loop.get_task_factory())


if sys.version_info >= (3, 12):

    def eager(loop: asyncio.BaseEventLoop) -> None:
        loop.set_task_factory(asyncio.eager_task_factory)
        loop.set_task_factory(asyncio.create_eager_task_factory(asyncio.Task))
