from typing import TypeVar

from docutils import readers

_S = TypeVar("_S", bound=str | bytes)

class Reader(readers.ReReader[_S]): ...
