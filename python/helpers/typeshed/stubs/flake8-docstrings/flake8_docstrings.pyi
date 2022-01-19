import argparse
import ast
from typing import Any, ClassVar, Generator, Iterable, Type

class pep257Checker:
    name: ClassVar[str]
    version: ClassVar[str]
    tree: ast.AST
    filename: str
    checker: Any
    source: str
    def __init__(self, tree: ast.AST, filename: str, lines: Iterable[str]) -> None: ...
    @classmethod
    def add_options(cls, parser: Any) -> None: ...
    @classmethod
    def parse_options(cls, options: argparse.Namespace) -> None: ...
    def run(self) -> Generator[tuple[int, int, str, Type[Any]], None, None]: ...

def __getattr__(name: str) -> Any: ...  # incomplete
