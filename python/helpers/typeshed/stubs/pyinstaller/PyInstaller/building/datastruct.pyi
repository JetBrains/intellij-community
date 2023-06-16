# https://pyinstaller.org/en/stable/advanced-topics.html#the-toc-and-tree-classes
from collections.abc import Iterable, Sequence
from typing import ClassVar
from typing_extensions import Literal, LiteralString, SupportsIndex, TypeAlias

_TypeCode: TypeAlias = Literal["DATA", "BINARY", "EXTENSION", "OPTION"]
_TOCTuple: TypeAlias = tuple[str, str | None, _TypeCode | None]

class TOC(list[_TOCTuple]):
    filenames: set[str]
    def __init__(self, initlist: Iterable[_TOCTuple] | None = ...) -> None: ...
    def append(self, entry: _TOCTuple) -> None: ...
    def insert(self, pos: SupportsIndex, entry: _TOCTuple) -> None: ...
    def extend(self, other: Iterable[_TOCTuple]) -> None: ...

class Target:
    invcnum: ClassVar[int]
    tocfilename: LiteralString
    tocbasename: LiteralString
    dependencies: TOC

class Tree(Target, TOC):
    root: str | None
    prefix: str | None
    excludes: Sequence[str]
    typecode: _TypeCode
    def __init__(
        self, root: str | None = ..., prefix: str | None = ..., excludes: Sequence[str] | None = ..., typecode: _TypeCode = ...
    ) -> None: ...
    def assemble(self) -> None: ...
