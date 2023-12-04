from _typeshed import Incomplete
from typing import Any, ClassVar
from typing_extensions import Literal

from docutils import parsers
from docutils.parsers.rst import states

class Parser(parsers.Parser):
    config_section_dependencies: ClassVar[tuple[str, ...]]
    initial_state: Literal["Body", "RFC2822Body"]
    state_classes: Any
    inliner: Any
    def __init__(self, rfc2822: bool = False, inliner: Incomplete | None = None) -> None: ...

class DirectiveError(Exception):
    level: Any
    msg: str
    def __init__(self, level: Any, message: str) -> None: ...

class Directive:
    def __init__(
        self,
        name: str,
        arguments: list[Any],
        options: dict[str, Any],
        content: list[str],
        lineno: int,
        content_offset: int,
        block_text: str,
        state: states.RSTState,
        state_machine: states.RSTStateMachine,
    ) -> None: ...
    def __getattr__(self, name: str) -> Incomplete: ...

def convert_directive_function(directive_fn): ...
