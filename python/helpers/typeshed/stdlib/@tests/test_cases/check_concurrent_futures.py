from __future__ import annotations

import sys
from collections.abc import Callable, Iterator
from concurrent.futures import Future, ThreadPoolExecutor, as_completed
from typing import Literal
from typing_extensions import assert_type


class Parent: ...


class Child(Parent): ...


def check_as_completed_covariance() -> None:
    with ThreadPoolExecutor() as executor:
        f1 = executor.submit(lambda: Parent())
        f2 = executor.submit(lambda: Child())
        fs: list[Future[Parent] | Future[Child]] = [f1, f2]
        assert_type(as_completed(fs), Iterator[Future[Parent]])
        for future in as_completed(fs):
            assert_type(future.result(), Parent)


def check_future_invariance() -> None:
    def execute_callback(callback: Callable[[], Parent], future: Future[Parent]) -> None:
        future.set_result(callback())

    fut: Future[Child] = Future()
    execute_callback(lambda: Parent(), fut)  # type: ignore
    assert isinstance(fut.result(), Child)


if sys.version_info >= (3, 14):

    def _initializer(x: int) -> None:
        pass

    def check_interpreter_pool_executor() -> None:
        import concurrent.futures.interpreter
        from concurrent.futures import InterpreterPoolExecutor

        with InterpreterPoolExecutor(initializer=_initializer, initargs=(1,)):
            ...

        with InterpreterPoolExecutor(initializer=_initializer, initargs=("x",)):  # type: ignore
            ...

        context = InterpreterPoolExecutor.prepare_context(initializer=_initializer, initargs=(1,))
        worker_context = context[0]()
        assert_type(worker_context, concurrent.futures.interpreter.WorkerContext)
        resolve_task = context[1]
        # Function should enforce that the arguments are correct.
        res = resolve_task(_initializer, 1)
        assert_type(res, tuple[bytes, Literal["function"]])
        # When the function is a script, the arguments should be a string.
        str_res = resolve_task("print('Hello, world!')")
        assert_type(str_res, tuple[bytes, Literal["script"]])
        # When a script is passed, no arguments should be provided.
        resolve_task("print('Hello, world!')", 1)  # type: ignore

        # `WorkerContext.__init__` should accept the result of a  resolved task.
        concurrent.futures.interpreter.WorkerContext(initdata=res)

        # Run should also accept the result of a resolved task.
        worker_context.run(res)

    def check_thread_worker_context() -> None:
        import concurrent.futures.thread

        context = concurrent.futures.thread.WorkerContext.prepare(initializer=_initializer, initargs=(1,))
        worker_context = context[0]()
        assert_type(worker_context, concurrent.futures.thread.WorkerContext)
        resolve_task = context[1]
        res = resolve_task(_initializer, (1,), {"test": 1})
        assert_type(res[1], tuple[int])
        assert_type(res[2], dict[str, int])
