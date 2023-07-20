# https://pyinstaller.org/en/stable/hooks.html#the-pre-safe-import-module-psim-api-method

# The documentation explicitely mentions that "Normally you do not need to know about the module-graph."
# However, some PyiModuleGraph typed class attributes are still documented as existing in imphookapi.
from _typeshed import Incomplete, StrPath, SupportsKeysAndGetItem
from collections.abc import Iterable
from typing_extensions import TypeAlias

from PyInstaller.lib.modulegraph.modulegraph import Alias, Node

_LazyNode: TypeAlias = Iterable[Node] | Iterable[str] | Alias | None
# from altgraph.Graph import Graph
_Graph: TypeAlias = Incomplete

class PyiModuleGraph:  # incomplete
    def __init__(
        self,
        pyi_homepath: str,
        user_hook_dirs: Iterable[StrPath] = ...,
        excludes: Iterable[str] = ...,
        *,
        path: Iterable[str] | None = ...,
        replace_paths: Iterable[tuple[StrPath, StrPath]] = ...,
        implies: SupportsKeysAndGetItem[str, _LazyNode] | Iterable[tuple[str, _LazyNode]] = ...,
        graph: _Graph | None = ...,
        debug: int = ...,
    ) -> None: ...
