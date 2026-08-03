from _typeshed import Incomplete, StrPath, SupportsRead
from collections.abc import Iterable

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["read_leda", "parse_leda"]

@_dispatchable
def read_leda(path: StrPath | SupportsRead[bytes], encoding: str = "UTF-8") -> Graph[Incomplete]: ...
@_dispatchable
def parse_leda(lines: str | Iterable[str]) -> Graph[Incomplete]: ...
