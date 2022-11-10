from _typeshed import Incomplete, SupportsWrite
from collections.abc import Iterable, Iterator, Mapping
from typing import Generic, NoReturn, TypeVar, overload

from .std import tqdm as std_tqdm, trange as trange

__all__ = ["tqdm_notebook", "tnrange", "tqdm", "trange"]

_T = TypeVar("_T")

class tqdm_notebook(Generic[_T], std_tqdm[_T]):
    @staticmethod
    def status_printer(
        _: SupportsWrite[str] | None, total: float | None = ..., desc: str | None = ..., ncols: int | None = ...
    ): ...
    displayed: bool
    def display(
        self,
        msg: str | None = ...,
        pos: int | None = ...,
        close: bool = ...,
        bar_style: str | None = ...,
        check_delay: bool = ...,
    ) -> None: ...
    @property
    def colour(self): ...
    @colour.setter
    def colour(self, bar_color: str) -> None: ...
    disp: Incomplete
    ncols: Incomplete
    container: Incomplete
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
        display: bool = ...,
        **kwargs,
    ) -> None: ...
    @overload
    def __init__(
        self: tqdm_notebook[NoReturn],
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
        display: bool = ...,
        **kwargs,
    ) -> None: ...
    def __iter__(self) -> Iterator[_T]: ...
    def update(self, n: int = ...): ...  # type: ignore[override]
    def close(self) -> None: ...
    def clear(self, *_, **__) -> None: ...
    def reset(self, total: float | None = ...): ...

tqdm = tqdm_notebook
tnrange = trange
