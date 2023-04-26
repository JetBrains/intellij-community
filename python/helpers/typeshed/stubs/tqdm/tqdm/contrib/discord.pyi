from _typeshed import Incomplete, SupportsWrite
from collections.abc import Iterable, Mapping
from typing import Generic, NoReturn, TypeVar, overload

from ..auto import tqdm as tqdm_auto
from .utils_worker import MonoWorker

__all__ = ["DiscordIO", "tqdm_discord", "tdrange", "tqdm", "trange"]

class DiscordIO(MonoWorker):
    text: Incomplete
    message: Incomplete
    def __init__(self, token, channel_id) -> None: ...
    def write(self, s): ...

_T = TypeVar("_T")

class tqdm_discord(Generic[_T], tqdm_auto[_T]):
    dio: Incomplete
    @overload
    def __init__(
        self,
        iterable: Iterable[_T],
        desc: str | None = ...,
        total: float | None = ...,
        leave: bool | None = ...,
        file: SupportsWrite[str] | None = ...,
        ncols: int | None = ...,
        mininterval: float = ...,
        maxinterval: float = ...,
        miniters: float | None = ...,
        ascii: bool | str | None = ...,
        disable: bool = ...,
        unit: str = ...,
        unit_scale: bool | float = ...,
        dynamic_ncols: bool = ...,
        smoothing: float = ...,
        bar_format: str | None = ...,
        initial: float = ...,
        position: int | None = ...,
        postfix: Mapping[str, object] | str | None = ...,
        unit_divisor: float = ...,
        write_bytes: bool | None = ...,
        lock_args: tuple[bool | None, float | None] | tuple[bool | None] | None = ...,
        nrows: int | None = ...,
        colour: str | None = ...,
        delay: float | None = ...,
        gui: bool = ...,
        **kwargs,
    ) -> None: ...
    @overload
    def __init__(
        self: tqdm_discord[NoReturn],
        iterable: None = ...,
        desc: str | None = ...,
        total: float | None = ...,
        leave: bool | None = ...,
        file: SupportsWrite[str] | None = ...,
        ncols: int | None = ...,
        mininterval: float = ...,
        maxinterval: float = ...,
        miniters: float | None = ...,
        ascii: bool | str | None = ...,
        disable: bool = ...,
        unit: str = ...,
        unit_scale: bool | float = ...,
        dynamic_ncols: bool = ...,
        smoothing: float = ...,
        bar_format: str | None = ...,
        initial: float = ...,
        position: int | None = ...,
        postfix: Mapping[str, object] | str | None = ...,
        unit_divisor: float = ...,
        write_bytes: bool | None = ...,
        lock_args: tuple[bool | None, float | None] | tuple[bool | None] | None = ...,
        nrows: int | None = ...,
        colour: str | None = ...,
        delay: float | None = ...,
        gui: bool = ...,
        **kwargs,
    ) -> None: ...
    def display(
        self,
        msg: str | None = ...,
        pos: int | None = ...,
        close: bool = ...,
        bar_style: Incomplete = ...,
        check_delay: bool = ...,
    ) -> None: ...
    def clear(self, *args, **kwargs) -> None: ...

def tdrange(*args, **kwargs) -> tqdm_discord[int]: ...

tqdm = tqdm_discord
trange = tdrange
