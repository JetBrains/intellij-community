import sys
from _typeshed import SupportsWrite
from collections.abc import Callable, Generator, Iterable, Mapping
from contextlib import contextmanager
from multiprocessing.context import BaseContext
from typing import Any, TypedDict, TypeVar, overload, type_check_only
from typing_extensions import Unpack

from ..std import tqdm

__all__ = ["thread_map", "process_map", "interpreter_map"]

_R = TypeVar("_R")
_T1 = TypeVar("_T1")
_T2 = TypeVar("_T2")
_T3 = TypeVar("_T3")
_T4 = TypeVar("_T4")
_T5 = TypeVar("_T5")

@type_check_only
class _TqdmCommonKwargs(TypedDict, total=False):
    # Concurrent-specific parameters
    tqdm_class: type[tqdm[object]]
    max_workers: int | None
    chunksize: int
    # Standard tqdm parameters
    desc: str | None
    total: float | None
    leave: bool | None
    file: SupportsWrite[str] | None
    ncols: int | None
    mininterval: float
    maxinterval: float
    miniters: float | None
    ascii: bool | str | None
    disable: bool | None
    unit: str
    unit_scale: bool | float
    dynamic_ncols: bool
    smoothing: float
    bar_format: str | None
    initial: float
    position: int | None
    postfix: Mapping[str, object] | str | None
    unit_divisor: float
    write_bytes: bool | None
    lock_args: tuple[bool | None, float | None] | tuple[bool | None] | None
    nrows: int | None
    colour: str | None
    delay: float | None

# TODO: refactor this, when `TypedDict` will support conditional fields
if sys.version_info >= (3, 14):
    @type_check_only
    class _TqdmKwargs(_TqdmCommonKwargs, total=False):
        buffersize: int | None

else:
    _TqdmKwargs = _TqdmCommonKwargs

@type_check_only
class _TqdmProcessKwargs(_TqdmKwargs, total=False):
    mp_context: BaseContext | None
    max_tasks_per_child: int | None

@type_check_only
class _TqdmThreadKwargs(_TqdmKwargs, total=False):
    thread_name_prefix: str | None
    # Not technically for threading, but just a signature difference:
    lock_name: str

@contextmanager
def ensure_lock(tqdm_class: type[tqdm[object]], lock_name: str = "", lock=None) -> Generator[None]: ...

@overload
def thread_map(fn: Callable[[_T1], _R], iter1: Iterable[_T1], **tqdm_kwargs: Unpack[_TqdmThreadKwargs]) -> list[_R]: ...
@overload
def thread_map(
    fn: Callable[[_T1, _T2], _R], iter1: Iterable[_T1], iter2: Iterable[_T2], /, **tqdm_kwargs: Unpack[_TqdmThreadKwargs]
) -> list[_R]: ...
@overload
def thread_map(
    fn: Callable[[_T1, _T2, _T3], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def thread_map(
    fn: Callable[[_T1, _T2, _T3, _T4], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def thread_map(
    fn: Callable[[_T1, _T2, _T3, _T4, _T5], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    iter5: Iterable[_T5],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def thread_map(
    fn: Callable[..., _R],
    iter1: Iterable[Any],
    iter2: Iterable[Any],
    iter3: Iterable[Any],
    iter4: Iterable[Any],
    iter5: Iterable[Any],
    iter6: Iterable[Any],
    *iterables: Iterable[Any],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...

@overload
def process_map(
    fn: Callable[[_T1], _R], iter1: Iterable[_T1], *, lock_name: str = "mp_lock", **tqdm_kwargs: Unpack[_TqdmProcessKwargs]
) -> list[_R]: ...
@overload
def process_map(
    fn: Callable[[_T1, _T2], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    *,
    lock_name: str = "mp_lock",
    **tqdm_kwargs: Unpack[_TqdmProcessKwargs],
) -> list[_R]: ...
@overload
def process_map(
    fn: Callable[[_T1, _T2, _T3], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    *,
    lock_name: str = "mp_lock",
    **tqdm_kwargs: Unpack[_TqdmProcessKwargs],
) -> list[_R]: ...
@overload
def process_map(
    fn: Callable[[_T1, _T2, _T3, _T4], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    *,
    lock_name: str = "mp_lock",
    **tqdm_kwargs: Unpack[_TqdmProcessKwargs],
) -> list[_R]: ...
@overload
def process_map(
    fn: Callable[[_T1, _T2, _T3, _T4, _T5], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    iter5: Iterable[_T5],
    *,
    lock_name: str = "mp_lock",
    **tqdm_kwargs: Unpack[_TqdmProcessKwargs],
) -> list[_R]: ...
@overload
def process_map(
    fn: Callable[..., _R],
    iter1: Iterable[Any],
    iter2: Iterable[Any],
    iter3: Iterable[Any],
    iter4: Iterable[Any],
    iter5: Iterable[Any],
    iter6: Iterable[Any],
    *iterables: Iterable[Any],
    lock_name: str = "mp_lock",
    **tqdm_kwargs: Unpack[_TqdmProcessKwargs],
) -> list[_R]: ...

@overload
def interpreter_map(fn: Callable[[_T1], _R], iter1: Iterable[_T1], **tqdm_kwargs: Unpack[_TqdmThreadKwargs]) -> list[_R]: ...
@overload
def interpreter_map(
    fn: Callable[[_T1, _T2], _R], iter1: Iterable[_T1], iter2: Iterable[_T2], /, **tqdm_kwargs: Unpack[_TqdmThreadKwargs]
) -> list[_R]: ...
@overload
def interpreter_map(
    fn: Callable[[_T1, _T2, _T3], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def interpreter_map(
    fn: Callable[[_T1, _T2, _T3, _T4], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def interpreter_map(
    fn: Callable[[_T1, _T2, _T3, _T4, _T5], _R],
    iter1: Iterable[_T1],
    iter2: Iterable[_T2],
    iter3: Iterable[_T3],
    iter4: Iterable[_T4],
    iter5: Iterable[_T5],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
@overload
def interpreter_map(
    fn: Callable[..., _R],
    iter1: Iterable[Any],
    iter2: Iterable[Any],
    iter3: Iterable[Any],
    iter4: Iterable[Any],
    iter5: Iterable[Any],
    iter6: Iterable[Any],
    *iterables: Iterable[Any],
    **tqdm_kwargs: Unpack[_TqdmThreadKwargs],
) -> list[_R]: ...
