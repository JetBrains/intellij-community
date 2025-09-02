"""
Tests consistency of overloads with implementation.
"""

from typing import Callable, Coroutine, overload
from types import CoroutineType

# > If an overload implementation is defined, type checkers should validate
# > that it is consistent with all of its associated overload signatures.
# > The implementation should accept all potential sets of arguments
# > that are accepted by the overloads and should produce all potential return
# > types produced by the overloads. In typing terms, this means the input
# > signature of the implementation should be :term:`assignable` to the input
# > signatures of all overloads, and the return type of all overloads should be
# > assignable to the return type of the implementation.

# Return type of all overloads must be assignable to return type of
# implementation:

@overload
def return_type(x: int) -> int:
    ...

@overload
def return_type(x: str) -> str:  # E[return_type]
    ...

def return_type(x: int | str) -> int:  # E[return_type] an overload returns `str`, not assignable to `int`
    return 1


# Input signature of implementation must be assignable to signature of each
# overload. We don't attempt a thorough testing of input signature
# assignability here; see `callables_subtyping.py` for that:

@overload
def parameter_type(x: int) -> int:
    ...

@overload
def parameter_type(x: str) -> str:  # E[parameter_type]
    ...

def parameter_type(x: int) -> int | str:  # E[parameter_type] impl type of `x` must be assignable from overload types of `x`
    return 1


# > Overloads are allowed to use a mixture of ``async def`` and ``def`` statements
# > within the same overload definition. Type checkers should convert
# > ``async def`` statements to a non-async signature (wrapping the return
# > type in a ``Coroutine``) before testing for implementation consistency
# > and overlapping overloads (described below).

# ...and also...

# > When a type checker checks the implementation for consistency with overloads,
# > it should first apply any transforms that change the effective type of the
# > implementation including the presence of a ``yield`` statement in the
# > implementation body, the use of ``async def``, and the presence of additional
# > decorators.

# An overload can explicitly return `Coroutine`, while the implementation is an
# `async def`:

@overload
def returns_coroutine(x: int) -> CoroutineType[None, None, int]:
    ...

@overload
async def returns_coroutine(x: str) -> str:
    ...

async def returns_coroutine(x: int | str) -> int | str:
    return 1


# The implementation can explicitly return `Coroutine`, while overloads are
# `async def`:

@overload
async def returns_coroutine_2(x: int) -> int:
    ...

@overload
async def returns_coroutine_2(x: str) -> str:
    ...

def returns_coroutine_2(x: int | str) -> Coroutine[None, None, int | str]:
    return _wrapped(x)

async def _wrapped(x: int | str) -> int | str:
    return 2

# Decorator transforms are applied before checking overload consistency:

def _deco_1(f: Callable) -> Callable[[int], int]:
    def wrapped(_x: int, /) -> int:
        return 1
    return wrapped

def _deco_2(f: Callable) -> Callable[[int | str], int | str]:
    def wrapped(_x: int | str, /) -> int | str:
        return 1
    return wrapped

@overload
@_deco_1
def decorated() -> None:
    ...

@overload
def decorated(x: str, /) -> str:
    ...

@_deco_2
def decorated(y: bytes, z: bytes) -> bytes:
    return b""

