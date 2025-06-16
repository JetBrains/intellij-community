import tkinter
from typing import Any

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
        bd: tkinter._ScreenUnits = ...,
        bg: str = ...,
        border: tkinter._ScreenUnits = ...,
        borderwidth: tkinter._ScreenUnits = ...,
        cursor: tkinter._Cursor = ...,
        height: tkinter._ScreenUnits = ...,
        highlightbackground: str = ...,
        highlightcolor: str = ...,
        highlightthickness: tkinter._ScreenUnits = ...,
        menu: tkinter.Menu = ...,
        padx: tkinter._ScreenUnits = ...,
        pady: tkinter._ScreenUnits = ...,
        relief: tkinter._Relief = ...,
        takefocus: tkinter._TakeFocusValue = ...,
        width: tkinter._ScreenUnits = ...,
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
        bd: tkinter._ScreenUnits = ...,
        bg: str = ...,
        border: tkinter._ScreenUnits = ...,
        borderwidth: tkinter._ScreenUnits = ...,
        cursor: tkinter._Cursor = ...,
        height: tkinter._ScreenUnits = ...,
        highlightbackground: str = ...,
        highlightcolor: str = ...,
        highlightthickness: tkinter._ScreenUnits = ...,
        menu: tkinter.Menu = ...,
        padx: tkinter._ScreenUnits = ...,
        pady: tkinter._ScreenUnits = ...,
        relief: tkinter._Relief = ...,
        takefocus: tkinter._TakeFocusValue = ...,
        width: tkinter._ScreenUnits = ...,
    ) -> dict[str, tuple[str, str, str, Any, Any]] | None: ...
    def __getitem__(self, k: str) -> Any: ...
    def __setitem__(self, k: str, v: Any) -> None: ...
