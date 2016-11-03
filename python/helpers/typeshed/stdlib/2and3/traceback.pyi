# Stubs for traceback

from typing import Generator, IO, Iterator, Mapping, Optional, Tuple, Type
from types import FrameType, TracebackType
import sys

_PT = Tuple[str, int, str, Optional[str]]


def print_tb(tb: TracebackType, limit: Optional[int] = ...,
             file: Optional[IO[str]] = ...) -> None: ...
if sys.version_info >= (3,):
    def print_exception(etype: Type[BaseException], value: BaseException,
                        tb: Optional[TracebackType], limit: Optional[int] = ...,
                        file: Optional[IO[str]] = ...,
                        chain: bool = ...) -> None: ...
    def print_exc(limit: Optional[int] = ..., file: Optional[IO[str]] = ...,
                  chain: bool = ...) -> None: ...
    def print_last(limit: Optional[int] = ..., file: Optional[IO[str]] = ...,
                   chain: bool = ...) -> None: ...
else:
    def print_exception(etype: Type[BaseException], value: BaseException,
                        tb: Optional[TracebackType], limit: Optional[int] = ...,
                        file: Optional[IO[str]] = ...) -> None: ...
    def print_exc(limit: Optional[int] = ...,
                  file: Optional[IO[str]] = ...) -> None: ...
    def print_last(limit: Optional[int] = ...,
                   file: Optional[IO[str]] = ...) -> None: ...
def print_stack(f: Optional[FrameType] = ..., limit: Optional[int] = ...,
                file: Optional[IO[str]] = ...) -> None: ...
def extract_tb(tb: TracebackType, limit: Optional[int] = ...) -> List[_PT]: ...
def extract_stack(f: Optional[FrameType] = ...,
                  limit: Optional[int] = ...) -> List[_PT]: ...
def format_list(extracted_list: List[_PT]) -> List[str]: ...
def format_exception_only(etype: Type[BaseException],
                          value: BaseException) -> List[str]: ...
if sys.version_info >= (3,):
    def format_exception(etype: Type[BaseException], value: BaseException,
                         tb: TracebackType, limit: Optional[int] = ...,
                         chain: bool = ...) -> List[str]: ...
    def format_exc(limit: Optional[int] = ..., chain: bool = ...) -> str: ...
else:
    def format_exception(etype: Type[BaseException], value: BaseException,
                         tb: TracebackType,
                         limit: Optional[int] = ...) -> List[str]: ...
    def format_exc(limit: Optional[int] = ...) -> str: ...
def format_tb(tb: TracebackType, limit: Optional[int] = ...) -> List[str]: ...
def format_stack(f: Optional[FrameType] = ...,
                 limit: Optional[int] = ...) -> List[str]: ...
if sys.version_info >= (3, 4):
    def clear_frames(tb: TracebackType) -> None: ...
if sys.version_info >= (3, 5):
    def walk_stack(f: Optional[FrameType]) -> Iterator[Tuple[FrameType, int]]: ...
    def walk_tb(tb: TracebackType) -> Iterator[Tuple[FrameType, int]]: ...
if sys.version_info < (3,):
    def tb_lineno(tb: TracebackType) -> int: ...


if sys.version_info >= (3, 5):
    class TracebackException:
        __cause__ = ...  # type:TracebackException
        __context__ = ...  # type:TracebackException
        __suppress_context__ = ...  # type: bool
        stack = ...  # type: StackSummary
        exc_type = ...  # type: Type[BaseException]
        filename = ...  # type: str
        lineno = ...  # type: int
        text = ...  # type: str
        offset = ...  # type: int
        msg = ...  # type: str
        def __init__(self, exc_type: Type[BaseException],
                     exc_value: BaseException, exc_traceback: TracebackType,
                     *, limit: Optional[int] = ..., lookup_lines: bool = ...,
                     capture_locals: bool = ...) -> None: ...
        @classmethod
        def from_exception(cls, exc: BaseException,
                           *, limit: Optional[int] = ...,
                           lookup_lines: bool = ...,
                           capture_locals: bool = ...) -> TracebackException: ...
        def format(self, *, chain: bool = ...) -> Generator[str, None, None]: ...
        def format_exception_only(self) -> Generator[str, None, None]: ...


if sys.version_info >= (3, 5):
    class StackSummary:
        @classmethod
        def extract(cls,
                    frame_gen: Generator[Tuple[FrameType, int], None, None],
                    *, limit: Optional[int] = ..., lookup_lines: bool = ...,
                    capture_locals: bool = ...) -> StackSummary: ...
        @classmethod
        def from_list(cls, a_list: List[_PT]) -> StackSummary: ...


if sys.version_info >= (3, 5):
    class FrameSummary:
        def __init__(self, filename: str, lineno: int, name: str,
                     lookup_line: bool = ...,
                     locals: Optional[Mapping[str, str]] = ...,
                     line: Optional[int] = ...) -> None: ...
