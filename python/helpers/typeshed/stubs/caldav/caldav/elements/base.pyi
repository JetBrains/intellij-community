from _typeshed import Self
from collections.abc import Iterable
from typing import Any, ClassVar
from typing_extensions import TypeAlias

_Element: TypeAlias = Any  # actually lxml.etree._Element

class BaseElement:
    tag: ClassVar[str | None]
    children: list[BaseElement]
    value: str | None
    attributes: Any | None
    caldav_class: Any | None
    def __init__(self, name: str | None = ..., value: str | bytes | None = ...) -> None: ...
    def __add__(self: Self, other: BaseElement) -> Self: ...
    def xmlelement(self) -> _Element: ...
    def xmlchildren(self, root: _Element) -> None: ...
    def append(self: Self, element: BaseElement | Iterable[BaseElement]) -> Self: ...

class NamedBaseElement(BaseElement):
    def __init__(self, name: str | None = ...) -> None: ...

class ValuedBaseElement(BaseElement):
    def __init__(self, value: str | bytes | None = ...) -> None: ...
