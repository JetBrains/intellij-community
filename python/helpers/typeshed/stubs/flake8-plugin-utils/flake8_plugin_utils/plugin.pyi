import argparse
import ast
from collections.abc import Iterable, Iterator
from typing import Any, Generic, TypeVar
from typing_extensions import TypeAlias

FLAKE8_ERROR: TypeAlias = tuple[int, int, str, type[Any]]
TConfig = TypeVar("TConfig")  # noqa: Y001  # Name of the TypeVar matches the name at runtime

class Error:
    code: str
    message: str
    lineno: int
    col_offset: int
    def __init__(self, lineno: int, col_offset: int, **kwargs: Any) -> None: ...
    @classmethod
    def formatted_message(cls, **kwargs: Any) -> str: ...

class Visitor(ast.NodeVisitor, Generic[TConfig]):
    errors: list[Error]
    def __init__(self, config: TConfig | None = ...) -> None: ...
    @property
    def config(self) -> TConfig: ...
    def error_from_node(self, error: type[Error], node: ast.AST, **kwargs: Any) -> None: ...

class Plugin(Generic[TConfig]):
    name: str
    version: str
    visitors: list[type[Visitor[TConfig]]]
    config: TConfig
    def __init__(self, tree: ast.AST) -> None: ...
    def run(self) -> Iterable[FLAKE8_ERROR]: ...
    @classmethod
    def parse_options(cls, option_manager: Any, options: argparse.Namespace, args: list[str]) -> None: ...
    @classmethod
    def parse_options_to_config(cls, option_manager: Any, options: argparse.Namespace, args: list[str]) -> TConfig | None: ...
    @classmethod
    def test_config(cls, config: TConfig) -> Iterator[None]: ...
