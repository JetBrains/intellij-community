import contextlib
import enum
import sys
from collections.abc import Callable, Iterable, Iterator
from typing import Any, ClassVar
from typing_extensions import Self

from pynput._util import AbstractListener

class KeyCode:
    _PLATFORM_EXTENSIONS: ClassVar[Iterable[str]]  # undocumented
    vk: int | None
    char: str | None
    is_dead: bool | None
    combining: str | None
    def __init__(self, vk: str | None = None, char: str | None = None, is_dead: bool = False, **kwargs: str) -> None: ...
    def __eq__(self, other: object) -> bool: ...
    def __hash__(self) -> int: ...
    def join(self, key: Self) -> Self: ...
    @classmethod
    def from_vk(cls, vk: int, **kwargs: Any) -> Self: ...
    @classmethod
    def from_char(cls, char: str, **kwargs: Any) -> Self: ...
    @classmethod
    def from_dead(cls, char: str, **kwargs: Any) -> Self: ...

class Key(enum.Enum):
    alt: KeyCode
    alt_l: KeyCode
    alt_r: KeyCode
    alt_gr: KeyCode
    backspace: KeyCode
    caps_lock: KeyCode
    cmd: KeyCode
    cmd_l: KeyCode
    cmd_r: KeyCode
    ctrl: KeyCode
    ctrl_l: KeyCode
    ctrl_r: KeyCode
    delete: KeyCode
    down: KeyCode
    end: KeyCode
    enter: KeyCode
    esc: KeyCode
    f1: KeyCode
    f2: KeyCode
    f3: KeyCode
    f4: KeyCode
    f5: KeyCode
    f6: KeyCode
    f7: KeyCode
    f8: KeyCode
    f9: KeyCode
    f10: KeyCode
    f11: KeyCode
    f12: KeyCode
    f13: KeyCode
    f14: KeyCode
    f15: KeyCode
    f16: KeyCode
    f17: KeyCode
    f18: KeyCode
    f19: KeyCode
    f20: KeyCode
    if sys.platform == "win32":
        f21: KeyCode
        f22: KeyCode
        f23: KeyCode
        f24: KeyCode
    home: KeyCode
    left: KeyCode
    page_down: KeyCode
    page_up: KeyCode
    right: KeyCode
    shift: KeyCode
    shift_l: KeyCode
    shift_r: KeyCode
    space: KeyCode
    tab: KeyCode
    up: KeyCode
    media_play_pause: KeyCode
    media_volume_mute: KeyCode
    media_volume_down: KeyCode
    media_volume_up: KeyCode
    media_previous: KeyCode
    media_next: KeyCode
    insert: KeyCode
    menu: KeyCode
    num_lock: KeyCode
    pause: KeyCode
    print_screen: KeyCode
    scroll_lock: KeyCode

class Controller:
    _KeyCode: ClassVar[type[KeyCode]]  # undocumented
    _Key: ClassVar[type[Key]]  # undocumented

    if sys.platform == "linux":
        CTRL_MASK: ClassVar[int]
        SHIFT_MASK: ClassVar[int]

    class InvalidKeyException(Exception): ...
    class InvalidCharacterException(Exception): ...

    def __init__(self) -> None: ...
    def press(self, key: str | Key | KeyCode) -> None: ...
    def release(self, key: str | Key | KeyCode) -> None: ...
    def tap(self, key: str | Key | KeyCode) -> None: ...
    def touch(self, key: str | Key | KeyCode, is_press: bool) -> None: ...
    @contextlib.contextmanager
    def pressed(self, *args: str | Key | KeyCode) -> Iterator[None]: ...
    def type(self, string: str) -> None: ...
    @property
    def modifiers(self) -> contextlib.AbstractContextManager[Iterator[set[Key]]]: ...
    @property
    def alt_pressed(self) -> bool: ...
    @property
    def alt_gr_pressed(self) -> bool: ...
    @property
    def ctrl_pressed(self) -> bool: ...
    @property
    def shift_pressed(self) -> bool: ...

class Listener(AbstractListener):
    def __init__(
        self,
        on_press: Callable[[Key | KeyCode | None], None] | None = None,
        on_release: Callable[[Key | KeyCode | None], None] | None = None,
        suppress: bool = False,
        **kwargs: Any,
    ) -> None: ...
    def canonical(self, key: Key | KeyCode) -> Key | KeyCode: ...
