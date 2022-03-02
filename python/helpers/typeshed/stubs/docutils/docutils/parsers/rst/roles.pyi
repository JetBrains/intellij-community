from typing import Any, Callable

import docutils.nodes
import docutils.parsers.rst.states

_RoleFn = Callable[
    [str, str, str, int, docutils.parsers.rst.states.Inliner, dict[str, Any], list[str]],
    tuple[list[docutils.nodes.reference], list[docutils.nodes.reference]],
]

def register_local_role(name: str, role_fn: _RoleFn) -> None: ...
def __getattr__(name: str) -> Any: ...  # incomplete
