from typing import Any, TypeVar


T = TypeVar('T')


def generic_kwargs(**kwargs: T) -> None:
    pass


generic_kwargs(a=1, b='foo')
