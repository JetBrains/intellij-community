from collections.abc import Iterable, Sequence
from typing import NoReturn

from ..collection import Collection
from ..parser import Parser, ParserContext, ParseResult

def complete(
    names: Iterable[str], core: ParseResult, initial_context: ParserContext, collection: Collection, parser: Parser
) -> NoReturn: ...
def print_task_names(collection: Collection) -> None: ...
def print_completion_script(shell: str, names: Sequence[str]) -> None: ...
