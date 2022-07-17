from typing import Any, Callable

import docutils.nodes
import docutils.parsers.rst.states
from docutils.languages import _LanguageModule
from docutils.utils import Reporter, SystemMessage

_RoleFn = Callable[
    [str, str, str, int, docutils.parsers.rst.states.Inliner, dict[str, Any], list[str]],
    tuple[list[docutils.nodes.reference], list[docutils.nodes.reference]],
]

def register_local_role(name: str, role_fn: _RoleFn) -> None: ...
def role(
    role_name: str, language_module: _LanguageModule, lineno: int, reporter: Reporter
) -> tuple[_RoleFn | None, list[SystemMessage]]: ...
def __getattr__(name: str) -> Any: ...  # incomplete
