from _typeshed import Incomplete

class Event:
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, start_mark=None, end_mark=None) -> None: ...

class NodeEvent(Event):
    anchor: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, anchor, start_mark=None, end_mark=None) -> None: ...

class CollectionStartEvent(NodeEvent):
    anchor: Incomplete
    tag: Incomplete
    implicit: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    flow_style: Incomplete
    def __init__(self, anchor, tag, implicit, start_mark=None, end_mark=None, flow_style=None) -> None: ...

class CollectionEndEvent(Event): ...

class StreamStartEvent(Event):
    start_mark: Incomplete
    end_mark: Incomplete
    encoding: Incomplete
    def __init__(self, start_mark=None, end_mark=None, encoding=None) -> None: ...

class StreamEndEvent(Event): ...

class DocumentStartEvent(Event):
    start_mark: Incomplete
    end_mark: Incomplete
    explicit: Incomplete
    version: Incomplete
    tags: Incomplete
    def __init__(self, start_mark=None, end_mark=None, explicit=None, version=None, tags=None) -> None: ...

class DocumentEndEvent(Event):
    start_mark: Incomplete
    end_mark: Incomplete
    explicit: Incomplete
    def __init__(self, start_mark=None, end_mark=None, explicit=None) -> None: ...

class AliasEvent(NodeEvent): ...

class ScalarEvent(NodeEvent):
    anchor: Incomplete
    tag: Incomplete
    implicit: Incomplete
    value: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    style: Incomplete
    def __init__(self, anchor, tag, implicit, value, start_mark=None, end_mark=None, style=None) -> None: ...

class SequenceStartEvent(CollectionStartEvent): ...
class SequenceEndEvent(CollectionEndEvent): ...
class MappingStartEvent(CollectionStartEvent): ...
class MappingEndEvent(CollectionEndEvent): ...
