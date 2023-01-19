# flake8-2020 has type annotations, but PEP 561 states:
# This PEP does not support distributing typing information as part of module-only distributions or single-file modules within namespace packages.
# Therefore typeshed is the best place.

import ast
from collections.abc import Generator
from typing import Any, ClassVar

class Plugin:
    name: ClassVar[str]
    version: ClassVar[str]
    def __init__(self, tree: ast.AST) -> None: ...
    def run(self) -> Generator[tuple[int, int, str, type[Any]], None, None]: ...

def __getattr__(name: str) -> Any: ...  # incomplete (other attributes are normally not accessed)
