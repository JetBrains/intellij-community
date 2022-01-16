from typing import IO, Any, Callable, Generator, Iterable, Text, TypeVar, overload

from click._termui_impl import ProgressBar as _ProgressBar
from click.core import _ConvertibleType

def hidden_prompt_func(prompt: str) -> str: ...
def _build_prompt(text: str, suffix: str, show_default: bool = ..., default: str | None = ...) -> str: ...
def prompt(
    text: str,
    default: str | None = ...,
    hide_input: bool = ...,
    confirmation_prompt: bool = ...,
    type: _ConvertibleType | None = ...,
    value_proc: Callable[[str | None], Any] | None = ...,
    prompt_suffix: str = ...,
    show_default: bool = ...,
    err: bool = ...,
    show_choices: bool = ...,
) -> Any: ...
def confirm(
    text: str, default: bool = ..., abort: bool = ..., prompt_suffix: str = ..., show_default: bool = ..., err: bool = ...
) -> bool: ...
def get_terminal_size() -> tuple[int, int]: ...
def echo_via_pager(
    text_or_generator: str | Iterable[str] | Callable[[], Generator[str, None, None]], color: bool | None = ...
) -> None: ...

_T = TypeVar("_T")

@overload
def progressbar(
    iterable: Iterable[_T],
    length: int | None = ...,
    label: str | None = ...,
    show_eta: bool = ...,
    show_percent: bool | None = ...,
    show_pos: bool = ...,
    item_show_func: Callable[[_T], str] | None = ...,
    fill_char: str = ...,
    empty_char: str = ...,
    bar_template: str = ...,
    info_sep: str = ...,
    width: int = ...,
    file: IO[Any] | None = ...,
    color: bool | None = ...,
) -> _ProgressBar[_T]: ...
@overload
def progressbar(
    iterable: None = ...,
    length: int | None = ...,
    label: str | None = ...,
    show_eta: bool = ...,
    show_percent: bool | None = ...,
    show_pos: bool = ...,
    item_show_func: Callable[[Any], str] | None = ...,
    fill_char: str = ...,
    empty_char: str = ...,
    bar_template: str = ...,
    info_sep: str = ...,
    width: int = ...,
    file: IO[Any] | None = ...,
    color: bool | None = ...,
) -> _ProgressBar[int]: ...
def clear() -> None: ...
def style(
    text: Text,
    fg: Text | None = ...,
    bg: Text | None = ...,
    bold: bool | None = ...,
    dim: bool | None = ...,
    underline: bool | None = ...,
    blink: bool | None = ...,
    reverse: bool | None = ...,
    reset: bool = ...,
) -> str: ...
def unstyle(text: Text) -> str: ...

# Styling options copied from style() for nicer type checking.
def secho(
    message: str | None = ...,
    file: IO[Any] | None = ...,
    nl: bool = ...,
    err: bool = ...,
    color: bool | None = ...,
    fg: str | None = ...,
    bg: str | None = ...,
    bold: bool | None = ...,
    dim: bool | None = ...,
    underline: bool | None = ...,
    blink: bool | None = ...,
    reverse: bool | None = ...,
    reset: bool = ...,
) -> None: ...
def edit(
    text: str | None = ...,
    editor: str | None = ...,
    env: str | None = ...,
    require_save: bool = ...,
    extension: str = ...,
    filename: str | None = ...,
) -> str: ...
def launch(url: str, wait: bool = ..., locate: bool = ...) -> int: ...
def getchar(echo: bool = ...) -> Text: ...
def pause(info: str = ..., err: bool = ...) -> None: ...
