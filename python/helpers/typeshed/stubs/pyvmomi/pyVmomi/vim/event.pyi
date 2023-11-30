from _typeshed import Incomplete
from datetime import datetime

def __getattr__(name: str) -> Incomplete: ...

class Event:
    createdTime: datetime

class EventFilterSpec:
    class ByTime:
        def __init__(self, beginTime: datetime) -> None: ...
    time: EventFilterSpec.ByTime

class EventManager:
    latestEvent: Event
    def QueryEvents(self, filer: EventFilterSpec) -> list[Event]: ...
