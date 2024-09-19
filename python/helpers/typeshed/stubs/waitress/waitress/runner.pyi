from _typeshed import Unused
from collections.abc import Callable, Sequence
from io import TextIOWrapper
from re import Pattern
from typing import Any, Final

HELP: Final[str]
RUNNER_PATTERN: Final[Pattern[str]]

def match(obj_name: str) -> tuple[str, str]: ...
def resolve(module_name: str, object_name: str) -> Any: ...  # Any module attribute
def show_help(stream: TextIOWrapper, name: str, error: str | None = None) -> None: ...
def show_exception(stream: TextIOWrapper) -> None: ...
def run(argv: Sequence[str] = ..., _serve: Callable[..., Unused] = ...) -> None: ...
