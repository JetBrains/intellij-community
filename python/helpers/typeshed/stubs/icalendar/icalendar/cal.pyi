from _typeshed import Incomplete, SupportsItems
from typing import Any, ClassVar, Final, Literal, overload

from pytz.tzinfo import DstTzInfo

from .caselessdict import CaselessDict
from .parser import Contentline, Contentlines
from .prop import TypesFactory

class ComponentFactory(CaselessDict[Incomplete]):
    def __init__(self, *args, **kwargs) -> None: ...

INLINE: CaselessDict[int]

class Component(CaselessDict[Incomplete]):
    name: ClassVar[str | None]
    required: ClassVar[tuple[str, ...]]
    singletons: ClassVar[tuple[str, ...]]
    multiple: ClassVar[tuple[str, ...]]
    exclusive: ClassVar[tuple[str, ...]]
    inclusive: ClassVar[tuple[tuple[str, ...], ...]]
    ignore_exceptions: ClassVar[bool]
    subcomponents: list[Incomplete]
    errors: list[str]

    def __init__(self, *args, **kwargs) -> None: ...
    def __bool__(self) -> bool: ...
    __nonzero__ = __bool__
    def is_empty(self) -> bool: ...
    @property
    def is_broken(self) -> bool: ...
    @overload
    def add(self, name: str, value: Any, *, encode: Literal[False]) -> None: ...
    @overload
    def add(self, name: str, value: Any, parameters: None, encode: Literal[False]) -> None: ...
    @overload
    def add(
        self, name: str, value: Any, parameters: SupportsItems[str, str | None] | None = None, encode: Literal[True] = True
    ) -> None: ...
    def decoded(self, name, default=[]): ...
    def get_inline(self, name, decode: bool = True): ...
    def set_inline(self, name, values, encode: bool = True) -> None: ...
    def add_component(self, component: Component) -> None: ...
    def walk(self, name: Incomplete | None = None): ...
    def property_items(self, recursive: bool = True, sorted: bool = True): ...
    @overload
    @classmethod
    def from_ical(cls, st: str, multiple: Literal[False] = False) -> Component: ...  # or any of its subclasses
    @overload
    @classmethod
    def from_ical(cls, st: str, multiple: Literal[True]) -> list[Component]: ...  # or any of its subclasses
    def content_line(self, name: str, value, sorted: bool = True) -> Contentline: ...
    def content_lines(self, sorted: bool = True) -> Contentlines: ...
    def to_ical(self, sorted: bool = True) -> bytes: ...
    def __eq__(self, other: Component) -> bool: ...  # type: ignore[override]

class Event(Component):
    name: ClassVar[Literal["VEVENT"]]

class Todo(Component):
    name: ClassVar[Literal["VTODO"]]

class Journal(Component):
    name: ClassVar[Literal["VJOURNAL"]]

class FreeBusy(Component):
    name: ClassVar[Literal["VFREEBUSY"]]

class Timezone(Component):
    name: ClassVar[Literal["VTIMEZONE"]]
    def to_tz(self) -> DstTzInfo: ...

class TimezoneStandard(Component):
    name: ClassVar[Literal["STANDARD"]]

class TimezoneDaylight(Component):
    name: ClassVar[Literal["DAYLIGHT"]]

class Alarm(Component):
    name: ClassVar[Literal["VALARM"]]

class Calendar(Component):
    name: ClassVar[Literal["VCALENDAR"]]

types_factory: Final[TypesFactory]
component_factory: Final[ComponentFactory]
