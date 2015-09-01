from typing import overload, TypeVar


@overload
def f(key: int) -> int: ...
@overload
def f(key: str) -> str: ...


def g(x: dict) -> None: ...


class C:
    @overload
    def __getitem__(self, key: int) -> int: ...
    @overload
    def __getitem__(self, key: str) -> str: ...
