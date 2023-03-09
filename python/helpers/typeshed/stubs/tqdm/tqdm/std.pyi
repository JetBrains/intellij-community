import contextlib
from _typeshed import Incomplete, Self, SupportsWrite
from collections.abc import Callable, Iterable, Iterator, Mapping, MutableMapping
from typing import Any, ClassVar, Generic, NoReturn, TypeVar, overload
from typing_extensions import Literal

from .utils import Comparable

__all__ = [
    "tqdm",
    "trange",
    "TqdmTypeError",
    "TqdmKeyError",
    "TqdmWarning",
    "TqdmExperimentalWarning",
    "TqdmDeprecationWarning",
    "TqdmMonitorWarning",
]

class TqdmTypeError(TypeError): ...
class TqdmKeyError(KeyError): ...

class TqdmWarning(Warning):
    def __init__(self, msg, fp_write: Incomplete | None = ..., *a, **k) -> None: ...

class TqdmExperimentalWarning(TqdmWarning, FutureWarning): ...
class TqdmDeprecationWarning(TqdmWarning, DeprecationWarning): ...
class TqdmMonitorWarning(TqdmWarning, RuntimeWarning): ...

_T = TypeVar("_T")

class tqdm(Generic[_T], Iterable[_T], Comparable):
    monitor_interval: ClassVar[int]

    @staticmethod
    def format_sizeof(num: float, suffix: str = ..., divisor: float = ...) -> str: ...
    @staticmethod
    def format_interval(t: float) -> str: ...
    @staticmethod
    def format_num(n: float) -> str: ...
    @staticmethod
    def status_printer(file: SupportsWrite[str]) -> Callable[[str], None]: ...
    @staticmethod
    def format_meter(
        n: float,
        total: float,
        elapsed: float,
        ncols: int | None = ...,
        prefix: str | None = ...,
        ascii: bool | str | None = ...,
        unit: str | None = ...,
        unit_scale: bool | float | None = ...,
        rate: float | None = ...,
        bar_format: str | None = ...,
        postfix: str | Mapping[str, object] | None = ...,
        unit_divisor: float | None = ...,
        initial: float | None = ...,
        colour: str | None = ...,
    ) -> str: ...
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
        **kwargs,
    ) -> None: ...
    @overload
    def __init__(
        self: tqdm[NoReturn],
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
        **kwargs,
    ) -> None: ...
    def __new__(cls: type[Self], *_, **__) -> Self: ...
    @classmethod
    def write(cls, s: str, file: SupportsWrite[str] | None = ..., end: str = ..., nolock: bool = ...) -> None: ...
    @classmethod
    def external_write_mode(
        cls, file: SupportsWrite[str] | None = ..., nolock: bool = ...
    ) -> contextlib._GeneratorContextManager[None]: ...
    @classmethod
    def set_lock(cls, lock) -> None: ...
    @classmethod
    def get_lock(cls): ...
    @classmethod
    def pandas(
        cls,
        *,
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
    ) -> None: ...

    iterable: Incomplete
    disable: Incomplete
    pos: Incomplete
    n: Incomplete
    total: Incomplete
    leave: Incomplete
    desc: Incomplete
    fp: Incomplete
    ncols: Incomplete
    nrows: Incomplete
    mininterval: Incomplete
    maxinterval: Incomplete
    miniters: Incomplete
    dynamic_miniters: Incomplete
    ascii: Incomplete
    unit: Incomplete
    unit_scale: Incomplete
    unit_divisor: Incomplete
    initial: Incomplete
    lock_args: Incomplete
    delay: Incomplete
    gui: Incomplete
    dynamic_ncols: Incomplete
    smoothing: Incomplete
    bar_format: Incomplete
    postfix: Incomplete
    colour: Incomplete
    last_print_n: Incomplete
    sp: Incomplete
    last_print_t: Incomplete
    start_t: Incomplete

    def __bool__(self) -> bool: ...
    def __nonzero__(self) -> bool: ...
    def __len__(self) -> int: ...
    def __reversed__(self) -> Iterator[_T]: ...
    def __contains__(self, item: object) -> bool: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> None: ...
    def __del__(self) -> None: ...
    def __hash__(self) -> int: ...
    def __iter__(self) -> Iterator[_T]: ...
    def update(self, n: float | None = ...) -> bool | None: ...
    def close(self) -> None: ...
    def clear(self, nolock: bool = ...) -> None: ...
    def refresh(
        self, nolock: bool = ..., lock_args: tuple[bool | None, float | None] | tuple[bool | None] | None = ...
    ) -> None: ...
    def unpause(self) -> None: ...
    def reset(self, total: float | None = ...) -> None: ...
    def set_description(self, desc: str | None = ..., refresh: bool | None = ...) -> None: ...
    def set_description_str(self, desc: str | None = ..., refresh: bool | None = ...) -> None: ...
    def set_postfix(self, ordered_dict: Mapping[str, object] | None = ..., refresh: bool | None = ..., **kwargs) -> None: ...
    def set_postfix_str(self, s: str = ..., refresh: bool = ...) -> None: ...
    def moveto(self, n) -> None: ...
    @property
    def format_dict(self) -> MutableMapping[str, Any]: ...
    def display(self, msg: str | None = ..., pos: int | None = ...) -> None: ...
    @classmethod
    def wrapattr(
        cls, stream, method: Literal["read", "write"], total: float | None = ..., bytes: bool | None = ..., **tqdm_kwargs
    ) -> contextlib._GeneratorContextManager[Incomplete]: ...

@overload
def trange(
    start: int,
    stop: int,
    step: int | None = ...,
    *,
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
) -> tqdm[int]: ...
@overload
def trange(
    stop: int,
    *,
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
) -> tqdm[int]: ...
