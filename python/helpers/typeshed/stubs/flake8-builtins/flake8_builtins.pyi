import ast
from typing import Any, ClassVar, Generator

class BuiltinsChecker:
    name: ClassVar[str]
    version: ClassVar[str]
    def __init__(self, tree: ast.AST, filename: str) -> None: ...
    def run(self) -> Generator[tuple[int, int, str, type[Any]], None, None]: ...

def __getattr__(name: str) -> Any: ...  # incomplete (other attributes are normally not accessed)
