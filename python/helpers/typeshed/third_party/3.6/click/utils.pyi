from typing import Any, Callable, Iterator, IO, List, Optional, TypeVar, Union


_T = TypeVar('_T')
_Decorator = Callable[[_T], _T]


def _posixify(name: str) -> str:
    ...


def safecall(func: _T) -> _T:
    ...


def make_str(value: Any) -> str:
    ...


def make_default_short_help(help: str, max_length: int = 45):
    ...


class LazyFile:
    name: str
    mode: str
    encoding: Optional[str]
    errors: str
    atomic: bool

    def __init__(
        self,
        filename: str,
        mode: str = 'r',
        encoding: str = None,
        errors: str = 'strict',
        atomic: bool = False
    ) -> None:
        ...

    def open(self) -> IO:
        ...

    def close(self) -> None:
        ...

    def close_intelligently(self) -> None:
        ...

    def __enter__(self) -> 'LazyFile':
        ...

    def __exit__(self, exc_type, exc_value, tb):
        ...

    def __iter__(self) -> Iterator:
        ...


class KeepOpenFile:
    _file: IO

    def __init__(self, file: IO) -> None:
        ...

    def __enter__(self) -> 'KeepOpenFile':
        ...

    def __exit__(self, exc_type, exc_value, tb):
        ...

    def __iter__(self) -> Iterator:
        ...


def echo(
    message: str = None,
    file: IO = None,
    nl: bool = True,
    err: bool = False,
    color: bool = None,
) -> None:
    ...


def get_binary_stream(name: str) -> IO[bytes]:
    ...


def get_text_stream(
    name: str, encoding: str = None, errors: str = 'strict'
) -> IO[str]:
    ...


def open_file(
    filename: str,
    mode: str = 'r',
    encoding: str = None,
    errors: str = 'strict',
    lazy: bool = False,
    atomic: bool = False
) -> Union[IO, LazyFile, KeepOpenFile]:
    ...


def get_os_args() -> List[str]:
    ...


def format_filename(filename: str, shorten: bool = False) -> str:
    ...


def get_app_dir(
    app_name: str, roaming: bool = True, force_posix: bool = False
) -> str:
    ...
