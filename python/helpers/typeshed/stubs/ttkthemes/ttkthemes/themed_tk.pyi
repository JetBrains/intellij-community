import tkinter
from collections.abc import Callable
from typing import Any, Literal

from ._widget import ThemedWidget

class ThemedTk(tkinter.Tk, ThemedWidget):
    def __init__(
        self,
        # non-keyword-only args copied from tkinter.Tk
        screenName: str | None = ...,
        baseName: str | None = ...,
        className: str = ...,
        useTk: bool = ...,
        sync: bool = ...,
        use: str | None = ...,
        *,
        theme: str | None = ...,
        # fonts argument does nothing
        toplevel: bool | None = ...,
        themebg: bool | None = ...,
        background: bool | None = ...,  # old alias for themebg
        gif_override: bool = ...,
    ) -> None: ...
    def set_theme(self, theme_name: str, toplevel: bool | None = None, themebg: bool | None = None) -> None: ...
    # Keep this in sync with tkinter.Tk
    def config(  # type: ignore[override]
        self,
        kw: dict[str, Any] | None = None,
        *,
        themebg: bool | None = ...,
        toplevel: bool | None = ...,
        theme: str | None = ...,
        background: str = ...,
        bd: float | str = ...,
        bg: str = ...,
        border: float | str = ...,
        borderwidth: float | str = ...,
        cursor: tkinter._Cursor = ...,
        height: float | str = ...,
        highlightbackground: str = ...,
        highlightcolor: str = ...,
        highlightthickness: float | str = ...,
        menu: tkinter.Menu = ...,
        padx: float | str = ...,
        pady: float | str = ...,
        relief: Literal["raised", "sunken", "flat", "ridge", "solid", "groove"] = ...,
        takefocus: bool | Literal[0, 1, ""] | Callable[[str], bool | None] = ...,
        width: float | str = ...,
    ) -> dict[str, tuple[str, str, str, Any, Any]] | None: ...
    def cget(self, k: str) -> Any: ...
    def configure(  # type: ignore[override]
        self,
        kw: dict[str, Any] | None = None,
        *,
        themebg: bool | None = ...,
        toplevel: bool | None = ...,
        theme: str | None = ...,
        background: str = ...,
        bd: float | str = ...,
        bg: str = ...,
        border: float | str = ...,
        borderwidth: float | str = ...,
        cursor: tkinter._Cursor = ...,
        height: float | str = ...,
        highlightbackground: str = ...,
        highlightcolor: str = ...,
        highlightthickness: float | str = ...,
        menu: tkinter.Menu = ...,
        padx: float | str = ...,
        pady: float | str = ...,
        relief: Literal["raised", "sunken", "flat", "ridge", "solid", "groove"] = ...,
        takefocus: bool | Literal[0, 1, ""] | Callable[[str], bool | None] = ...,
        width: float | str = ...,
    ) -> dict[str, tuple[str, str, str, Any, Any]] | None: ...
    def __getitem__(self, k: str) -> Any: ...
    def __setitem__(self, k: str, v: Any) -> None: ...
