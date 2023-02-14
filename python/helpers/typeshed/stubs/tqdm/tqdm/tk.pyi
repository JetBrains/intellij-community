from _typeshed import Incomplete, SupportsWrite
from collections.abc import Iterable, Mapping
from typing import Generic, NoReturn, TypeVar, overload

from .std import tqdm as std_tqdm

__all__ = ["tqdm_tk", "ttkrange", "tqdm", "trange"]

_T = TypeVar("_T")

class tqdm_tk(Generic[_T], std_tqdm[_T]):
    @overload
    def __init__(
        self,
        iterable: Iterable[_T],
        desc: str | None = ...,
        total: float | None = ...,
        leave: bool = ...,
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
        grab=...,
        tk_parent=...,
        cancel_callback=...,
        **kwargs,
    ) -> None: ...
    @overload
    def __init__(
        self: tqdm_tk[NoReturn],
        iterable: None = ...,
        desc: str | None = ...,
        total: float | None = ...,
        leave: bool = ...,
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
        grab=...,
        tk_parent=...,
        cancel_callback=...,
        **kwargs,
    ) -> None: ...
    disable: bool
    def close(self) -> None: ...
    def clear(self, *_, **__) -> None: ...
    def display(self, *_, **__) -> None: ...
    def set_description(self, desc: str | None = ..., refresh: bool | None = ...) -> None: ...
    desc: Incomplete
    def set_description_str(self, desc: str | None = ..., refresh: bool | None = ...) -> None: ...
    def cancel(self) -> None: ...
    def reset(self, total: Incomplete | None = ...) -> None: ...

def ttkrange(*args, **kwargs) -> tqdm_tk[int]: ...

tqdm = tqdm_tk
trange = ttkrange
