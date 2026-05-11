from collections.abc import Callable, Sequence
from typing import TypeAlias

_Algorithm: TypeAlias = Callable[[int], Sequence[int]]

def method(algorithm: _Algorithm, alphabet: str, size: int) -> str: ...
