# Referenced in: https://pyinstaller.org/en/stable/advanced-topics.html#module-pyi_splash
# Source: https://github.com/pyinstaller/pyinstaller/blob/develop/PyInstaller/fake-modules/pyi_splash.py
from typing_extensions import Literal

__all__ = ["CLOSE_CONNECTION", "FLUSH_CHARACTER", "is_alive", "close", "update_text"]

def is_alive() -> bool: ...
def update_text(msg: str) -> None: ...
def close() -> None: ...

CLOSE_CONNECTION: Literal[b"\u0004"]
FLUSH_CHARACTER: Literal[b"\r"]
