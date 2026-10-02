from _typeshed import FileDescriptorLike
from collections.abc import Mapping
from selectors import BaseSelector, SelectorKey
from typing import Any

from gevent._util import Lazy
from gevent.hub import Hub

__all__ = ["DefaultSelector", "GeventSelector"]

# technically this derives from _BaseSelectorImpl, which does not have type annotations
# but in terms of type checking the only difference is, that we need to add get_map since
# GeventSelector does not override it
class GeventSelector(BaseSelector):
    def __init__(self, hub: Hub | None = None) -> None: ...
    @Lazy
    def hub(self) -> Hub: ...
    def register(self, fileobj: FileDescriptorLike, events: int, data: Any = None) -> SelectorKey: ...
    def unregister(self, fileobj: FileDescriptorLike) -> SelectorKey: ...
    def select(self, timeout: float | None = None) -> list[tuple[SelectorKey, int]]: ...
    def close(self) -> None: ...
    def get_map(self) -> Mapping[FileDescriptorLike, SelectorKey]: ...

DefaultSelector = GeventSelector
