# Stubs for getpass

from typing import Optional, TextIO


def getpass(prompt: str = ..., stream: Optional[TextIO] = None) -> str: ...


def getuser() -> str: ...


class GetPassWarning(UserWarning):
    pass
