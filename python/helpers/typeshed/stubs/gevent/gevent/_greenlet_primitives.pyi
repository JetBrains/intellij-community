from abc import abstractmethod
from typing import Any
from typing_extensions import Never, disjoint_base

from gevent._types import _Loop
from greenlet import greenlet

class TrackedRawGreenlet(greenlet): ...

@disjoint_base
class SwitchOutGreenletWithLoop(TrackedRawGreenlet):
    @property
    @abstractmethod
    def loop(self) -> _Loop: ...
    @loop.setter
    def loop(self, value: _Loop) -> None: ...

    def switch(self) -> Any: ...
    def switch_out(self) -> Never: ...

__all__ = ["TrackedRawGreenlet", "SwitchOutGreenletWithLoop"]
