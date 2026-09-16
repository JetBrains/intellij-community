from collections.abc import Mapping
from typing import TypeAlias

from ._yaml import Mark as _CMark
from .error import Mark

_Mark: TypeAlias = Mark | _CMark

class Event:
    start_mark: _Mark | None
    end_mark: _Mark | None
    def __init__(self, start_mark: _Mark | None = None, end_mark: _Mark | None = None) -> None: ...

class NodeEvent(Event):
    anchor: str | None
    def __init__(self, anchor: str | None, start_mark: _Mark | None = None, end_mark: _Mark | None = None) -> None: ...

class CollectionStartEvent(NodeEvent):
    tag: str | None
    implicit: bool
    flow_style: bool | None
    def __init__(
        self,
        anchor: str | None,
        tag: str | None,
        implicit: bool,
        start_mark: _Mark | None = None,
        end_mark: _Mark | None = None,
        flow_style: bool | None = None,
    ) -> None: ...

class CollectionEndEvent(Event): ...

class StreamStartEvent(Event):
    encoding: str | None
    def __init__(self, start_mark: _Mark | None = None, end_mark: _Mark | None = None, encoding: str | None = None) -> None: ...

class StreamEndEvent(Event): ...

class DocumentStartEvent(Event):
    explicit: bool | None
    version: tuple[int, int] | None
    tags: Mapping[str, str] | None
    def __init__(
        self,
        start_mark: _Mark | None = None,
        end_mark: _Mark | None = None,
        explicit: bool | None = None,
        version: tuple[int, int] | None = None,
        tags: Mapping[str, str] | None = None,
    ) -> None: ...

class DocumentEndEvent(Event):
    explicit: bool | None
    def __init__(self, start_mark: _Mark | None = None, end_mark: _Mark | None = None, explicit: bool | None = None) -> None: ...

class AliasEvent(NodeEvent): ...

class ScalarEvent(NodeEvent):
    tag: str | None
    implicit: tuple[bool, bool]
    value: str
    style: str | None
    def __init__(
        self,
        anchor: str | None,
        tag: str | None,
        implicit: tuple[bool, bool],
        value: str,
        start_mark: _Mark | None = None,
        end_mark: _Mark | None = None,
        style: str | None = None,
    ) -> None: ...

class SequenceStartEvent(CollectionStartEvent): ...
class SequenceEndEvent(CollectionEndEvent): ...
class MappingStartEvent(CollectionStartEvent): ...
class MappingEndEvent(CollectionEndEvent): ...
