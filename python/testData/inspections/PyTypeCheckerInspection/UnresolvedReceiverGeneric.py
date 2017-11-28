from typing import TypeVar, Dict, Iterable, Any

T = TypeVar("T")


def foo(values: Dict[T, Iterable[Any]]):
    for e in []:
        values.setdefault(e, None)