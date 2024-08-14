from collections.abc import Callable
from typing import TypeVar

_F = TypeVar("_F", bound=Callable[..., object])

class requires_geos:
    version: tuple[int, int, int]
    def __init__(self, version: str) -> None: ...
    def __call__(self, func: _F) -> _F: ...

def multithreading_enabled(func: _F) -> _F: ...
