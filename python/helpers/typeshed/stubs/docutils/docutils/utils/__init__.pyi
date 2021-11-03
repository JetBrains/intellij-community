from collections.abc import Iterable
from typing import Any

from docutils.io import FileOutput

_list = list

class DependencyList:
    list: _list[str]
    file: FileOutput | None
    def __init__(self, output_file: str | None = ..., dependencies: Iterable[str] = ...) -> None: ...
    def set_output(self, output_file: str | None) -> None: ...
    def add(self, *filenames: str) -> None: ...
    def close(self) -> None: ...

def __getattr__(name: str) -> Any: ...  # incomplete
