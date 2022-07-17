import enum
from _typeshed import Self
from collections.abc import Callable
from types import TracebackType
from typing import Any

from pynput._util import AbstractListener

class Button(enum.Enum):
    unknown: int
    left: int
    middle: int
    right: int

class Controller:
    def __init__(self) -> None: ...
    @property
    def position(self) -> tuple[int, int]: ...
    @position.setter
    def position(self, position: tuple[int, int]) -> None: ...
    def scroll(self, dx: int, dy: int) -> None: ...
    def press(self, button: Button) -> None: ...
    def release(self, button: Button) -> None: ...
    def move(self, dx: int, dy: int) -> None: ...
    def click(self, button: Button, count: int = ...) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...

class Listener(AbstractListener):
    def __init__(
        self,
        on_move: Callable[[int, int], bool | None] | None = ...,
        on_click: Callable[[int, int, Button, bool], bool | None] | None = ...,
        on_scroll: Callable[[int, int, int, int], bool | None] | None = ...,
        suppress: bool = ...,
        **kwargs: Any,
    ) -> None: ...
