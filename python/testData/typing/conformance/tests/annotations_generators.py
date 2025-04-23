"""
Tests for annotating generators.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/annotations.html#annotating-generator-functions-and-coroutines

# The return type of generator functions can be annotated by the generic type
# Generator[yield_type, send_type, return_type] provided by typing.py module.

import asyncio
from typing import (
    Any,
    AsyncGenerator,
    AsyncIterable,
    AsyncIterator,
    Awaitable,
    Callable,
    Coroutine,
    Generator,
    Iterable,
    Iterator,
    Protocol,
    TypeVar,
    assert_type,
)

T = TypeVar("T")


class A:
    pass


class B:
    def should_continue(self) -> bool:
        return True


class C:
    pass


def generator1() -> Generator[A, B, C]:
    cont = B()
    while cont.should_continue():
        yield A()

    return C()


def generator2() -> Generator[A, B, C]:  # E: missing return
    cont = B()
    if cont.should_continue():
        return False  # E: incompatible return type

    while cont.should_continue():
        yield 3  # E: incompatible yield type


def generator3() -> Generator[A, int, Any]:
    cont = B()
    if cont.should_continue():
        return 3

    while cont.should_continue():
        yield 3  # E: Incompatible yield type


def generator4() -> Iterable[A]:
    yield A()
    return True  # E?: No return value expected


def generator5() -> Iterator[A]:
    yield B()  # E: incompatible yield type


def generator6() -> Generator[None, None, None]:
    yield


def generator7() -> Iterator[dict[str, int]]:
    yield {"": 0}  # OK


def generator8() -> int:  # E: incompatible return type
    yield None  # E
    return 0


async def generator9() -> int:  # E: incompatible return type
    yield None  # E


class IntIterator(Protocol):
    def __next__(self, /) -> int:
        ...


def generator15() -> IntIterator:  # OK
    yield 0


class AsyncIntIterator(Protocol):
    def __anext__(self, /) -> Awaitable[int]:
        ...


async def generator16() -> AsyncIntIterator:  # OK
    yield 0


def generator17() -> Iterator[A]:  # OK
    yield from generator17()


def generator18() -> Iterator[B]:
    yield from generator17()  # E: incompatible generator type
    yield from [1]  # E: incompatible generator type


def generator19() -> Generator[None, float, None]:  # OK
    x: float = yield


def generator20() -> Generator[None, int, None]:  # OK
    yield from generator19()


def generator21() -> Generator[None, int, None]:
    x: float = yield


def generator22() -> Generator[None, str, None]:
    yield from generator21()  # E: incompatible send type


def generator23() -> Iterable[str]:  # OK
    return
    yield ""  # Unreachable


async def generator24() -> AsyncIterable[str]:  # OK
    return
    yield ""  # Unreachable


def generator25(ints1: list[int], ints2: list[int]) -> Generator[int, None, None]:  # OK
    yield from ints1
    yield from ints2


async def get_data() -> list[int]:
    await asyncio.sleep(1)
    return [1, 2, 3]


async def generator26(nums: list[int]) -> AsyncGenerator[str, None]:
    for n in nums:
        await asyncio.sleep(1)
        yield f"The number is {n}"


async def generator27() -> AsyncGenerator[str, None]:
    data = await get_data()
    v1 = generator26(data)
    assert_type(v1, AsyncGenerator[str, None])
    return v1


async def generator28() -> AsyncIterator[str]:
    data = await get_data()
    v1 = generator26(data)
    assert_type(v1, AsyncGenerator[str, None])
    return v1


async def generator29() -> AsyncIterator[int]:
    raise NotImplementedError


# Don't use assert_type here because some type checkers infer
# the narrower type types.CoroutineType rather than typing.Coroutine
# in this case.
v1: Callable[[], Coroutine[Any, Any, AsyncIterator[int]]] = generator29


async def generator30() -> AsyncIterator[int]:
    raise NotImplementedError
    yield


assert_type(generator30, Callable[[], AsyncIterator[int]])
