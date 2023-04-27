from _typeshed import Incomplete, SupportsWrite
from collections.abc import Iterable, Mapping
from typing import Any, Generic, NoReturn, TypeVar, overload
from typing_extensions import TypeAlias

from .std import tqdm as std_tqdm

__all__ = ["tqdm_rich", "trrange", "tqdm", "trange"]

_ProgressColumn: TypeAlias = Any  # Actually rich.progress.ProgressColumn

class FractionColumn(_ProgressColumn):
    unit_scale: bool
    unit_divisor: int

    def __init__(self, unit_scale: bool = ..., unit_divisor: int = ...) -> None: ...
    def render(self, task): ...

class RateColumn(_ProgressColumn):
    unit: str
    unit_scale: bool
    unit_divisor: int

    def __init__(self, unit: str = ..., unit_scale: bool = ..., unit_divisor: int = ...) -> None: ...
    def render(self, task): ...

_T = TypeVar("_T")

class tqdm_rich(Generic[_T], std_tqdm[_T]):
    def close(self) -> None: ...
    def clear(self, *_, **__) -> None: ...
    def display(self, *_, **__) -> None: ...
    def reset(self, total: Incomplete | None = ...) -> None: ...
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
        self: tqdm_rich[NoReturn],
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

def trrange(*args, **kwargs) -> tqdm_rich[int]: ...

tqdm = tqdm_rich
trange = trrange
