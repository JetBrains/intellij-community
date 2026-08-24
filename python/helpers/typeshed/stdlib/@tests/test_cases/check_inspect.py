from __future__ import annotations

import inspect
from collections.abc import AsyncGenerator, Awaitable, Callable, Coroutine, Generator
from typing import Any
from typing_extensions import assert_type


def test_iscoroutinefunction_inspect(
    x: Callable[[str, int], Coroutine[str, int, bytes]],
    y: Callable[[str, int], Awaitable[bytes]],
    z: Callable[[str, int], str | Awaitable[bytes]],
    xx: object,
) -> None:
    if inspect.iscoroutinefunction(x):
        assert_type(x, Callable[[str, int], Coroutine[str, int, bytes]])

    if inspect.iscoroutinefunction(y):
        assert_type(y, Callable[[str, int], Coroutine[Any, Any, bytes]])

    if inspect.iscoroutinefunction(z):
        assert_type(z, Callable[[str, int], Coroutine[Any, Any, Any]])

    if inspect.iscoroutinefunction(xx):
        assert_type(xx, Callable[..., Coroutine[Any, Any, Any]])


def test_isgeneratorfunction_inspect(x: Callable[[str], object], y: object) -> None:
    if inspect.isgeneratorfunction(x):
        assert_type(x, Callable[[str], Generator[Any, Any, Any]])
    if inspect.isgeneratorfunction(y):
        assert_type(y, Callable[..., Generator[Any, Any, Any]])


def test_isasyncgenfunction_inspect(x: Callable[[str], object], y: object) -> None:
    if inspect.isasyncgenfunction(x):
        assert_type(x, Callable[[str], AsyncGenerator[Any, Any]])
    if inspect.isasyncgenfunction(y):
        assert_type(y, Callable[..., AsyncGenerator[Any, Any]])
