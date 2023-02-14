from collections.abc import Iterable
from typing import Any

from .collection import Collection
from .config import Config
from .parser import ParserContext, ParseResult
from .tasks import Call, Task

class Executor:
    collection: Collection
    config: Config
    core: ParseResult | None
    def __init__(self, collection: Collection, config: Config | None = ..., core: ParseResult | None = ...) -> None: ...
    def execute(self, *tasks: str | tuple[str, dict[str, Any]] | ParserContext) -> dict[Task, Any]: ...
    def normalize(self, tasks: Iterable[str | tuple[str, dict[str, Any]] | ParserContext]): ...
    def dedupe(self, calls: Iterable[Call]) -> list[Call]: ...
    def expand_calls(self, calls: Iterable[Call | Task]) -> list[Call]: ...
