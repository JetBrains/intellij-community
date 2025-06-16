#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any, Iterable, TypeVar

_T = TypeVar("_T")

StringTypes: tuple[type[str]]

class NodeList(list[_T]):
    length: int
    def item(self, index: int) -> _T | None: ...

class EmptyNodeList(tuple[Any, ...]):
    length: int
    def item(self, index: int) -> None: ...
    def __add__(self, other: Iterable[_T]) -> NodeList[_T]: ...  # type: ignore[override]
    def __radd__(self, other: Iterable[_T]) -> NodeList[_T]: ...

def defproperty(klass: type[Any], name: str, doc: str) -> None: ...
