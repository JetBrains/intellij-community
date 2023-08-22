from typing import Any

from .icalendar import VCalendar2_0

class HCalendar(VCalendar2_0):
    name: str
    @classmethod
    def serialize(cls, obj, buf: Any | None = ..., lineLength: Any | None = ..., validate: bool = ...): ...
