from typing import IO, List, Optional

from click.core import Context, Parameter


class ClickException(Exception):
    exit_code: int
    message: str

    def __init__(self, message: str) -> None:
        ...

    def format_message(self) -> str:
        ...

    def show(self, file=None) -> None:
        ...


class UsageError(ClickException):
    ctx: Optional[Context]

    def __init__(self, message: str, ctx: Context = None) -> None:
        ...

    def show(self, file: IO = None) -> None:
        ...


class BadParameter(UsageError):
    param: Optional[Parameter]
    param_hint: Optional[str]

    def __init__(
        self,
        message: str,
        ctx: Context = None,
        param: Parameter = None,
        param_hint: str = None
    ) -> None:
        ...


class MissingParameter(BadParameter):
    param_type: str  # valid values: 'parameter', 'option', 'argument'

    def __init__(
        self,
        message: str = None,
        ctx: Context = None,
        param: Parameter = None,
        param_hint: str = None,
        param_type: str = None
    ) -> None:
        ...


class NoSuchOption(UsageError):
    option_name: str
    possibilities: Optional[List[str]]

    def __init__(
        self,
        option_name: str,
        message: str = None,
        possibilities: List[str] = None,
        ctx: Context = None
    ) -> None:
        ...


class BadOptionUsage(UsageError):
    def __init__(self, message: str, ctx: Context = None) -> None:
        ...


class BadArgumentUsage(UsageError):
    def __init__(self, message: str, ctx: Context = None) -> None:
        ...


class FileError(ClickException):
    ui_filename: str
    filename: str

    def __init__(self, filename: str, hint: str = None) -> None:
        ...


class Abort(RuntimeError):
    ...
