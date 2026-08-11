from _typeshed import Incomplete, StrPath, SupportsRead, SupportsWrite
from collections.abc import Generator, Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["generate_adjlist", "write_adjlist", "parse_adjlist", "read_adjlist"]

def generate_adjlist(G: Graph[_Node], delimiter: str = " ") -> Generator[str]: ...
def write_adjlist(
    G: Graph[_Node], path: StrPath | SupportsWrite[bytes], comments: str = "#", delimiter: str = " ", encoding: str = "utf-8"
) -> None: ...
@_dispatchable
def parse_adjlist(
    lines: Iterable[str],
    comments: str = "#",
    delimiter: str | None = None,
    create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None,
    nodetype: type[Incomplete] | None = None,
) -> Graph[Incomplete]: ...
@_dispatchable
def read_adjlist(
    path: StrPath | SupportsRead[bytes],
    comments: str = "#",
    delimiter: str | None = None,
    create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None,
    nodetype: type[Incomplete] | None = None,
    encoding: str = "utf-8",
) -> Graph[Incomplete]: ...
