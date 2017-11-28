from contextlib import contextmanager
from typing import (
    Any,
    Callable,
    Generator,
    Iterable,
    IO,
    List,
    Optional,
    Tuple,
    TypeVar,
)


def hidden_prompt_func(prompt: str) -> str:
    ...


def _build_prompt(
    text: str,
    suffix: str,
    show_default: bool = False,
    default: Optional[str] = None,
) -> str:
    ...


def prompt(
    text: str,
    default: Optional[str] = None,
    hide_input: bool = False,
    confirmation_prompt: bool = False,
    type: Optional[Any] = None,
    value_proc: Optional[Callable[[Optional[str]], Any]] = None,
    prompt_suffix: str = ': ',
    show_default: bool = True,
    err: bool = False,
) -> Any:
    ...


def confirm(
    text: str,
    default: bool = False,
    abort: bool = False,
    prompt_suffix: str = ': ',
    show_default: bool = True,
    err: bool = False,
) -> bool:
    ...


def get_terminal_size() -> Tuple[int, int]:
    ...


def echo_via_pager(text: str, color: Optional[bool] = None) -> None:
    ...


_T = TypeVar('_T')


@contextmanager
def progressbar(
    iterable: Optional[Iterable[_T]] = None,
    length: Optional[int] = None,
    label: Optional[str] = None,
    show_eta: bool = True,
    show_percent: Optional[bool] = None,
    show_pos: bool = False,
    item_show_func: Optional[Callable[[_T], str]] = None,
    fill_char: str = '#',
    empty_char: str = '-',
    bar_template: str = '%(label)s  [%(bar)s]  %(info)s',
    info_sep: str = '  ',
    width: int = 36,
    file: Optional[IO] = None,
    color: Optional[bool] = None,
) -> Generator[_T, None, None]:
    ...


def clear() -> None:
    ...


def style(
    text: str,
    fg: Optional[str] = None,
    bg: Optional[str] = None,
    bold: Optional[bool] = None,
    dim: Optional[bool] = None,
    underline: Optional[bool] = None,
    blink: Optional[bool] = None,
    reverse: Optional[bool] = None,
    reset: bool = True,
):
    ...


def unstyle(text: str) -> str:
    ...


# Styling options copied from style() for nicer type checking.
def secho(
    text: str,
    file: Optional[IO] = None,
    nl: bool =True,
    err: bool = False,
    color: Optional[bool] = None,
    fg: Optional[str] = None,
    bg: Optional[str] = None,
    bold: Optional[bool] = None,
    dim: Optional[bool] = None,
    underline: Optional[bool] = None,
    blink: Optional[bool] = None,
    reverse: Optional[bool] = None,
    reset: bool = True,
):
    ...


def edit(
    text: Optional[str] = None,
    editor: Optional[str] = None,
    env: Optional[str] = None,
    require_save: bool = True,
    extension: str = '.txt',
    filename: Optional[str] = None,
) -> str:
    ...


def launch(url: str, wait: bool = False, locate: bool = False) -> int:
    ...


def getchar(echo: bool = False) -> str:
    ...


def pause(
    info: str ='Press any key to continue ...', err: bool = False
) -> None:
    ...
