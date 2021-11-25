from typing import Any, Iterable, Set, Tuple

from click.core import Context

def _unpack_args(args: Iterable[str], nargs_spec: Iterable[int]) -> tuple[Tuple[Tuple[str, ...] | None, ...], list[str]]: ...
def split_opt(opt: str) -> tuple[str, str]: ...
def normalize_opt(opt: str, ctx: Context) -> str: ...
def split_arg_string(string: str) -> list[str]: ...

class Option:
    dest: str
    action: str
    nargs: int
    const: Any
    obj: Any
    prefixes: Set[str]
    _short_opts: list[str]
    _long_opts: list[str]
    def __init__(
        self,
        opts: Iterable[str],
        dest: str,
        action: str | None = ...,
        nargs: int = ...,
        const: Any | None = ...,
        obj: Any | None = ...,
    ) -> None: ...
    @property
    def takes_value(self) -> bool: ...
    def process(self, value: Any, state: ParsingState) -> None: ...

class Argument:
    dest: str
    nargs: int
    obj: Any
    def __init__(self, dest: str, nargs: int = ..., obj: Any | None = ...) -> None: ...
    def process(self, value: Any, state: ParsingState) -> None: ...

class ParsingState:
    opts: dict[str, Any]
    largs: list[str]
    rargs: list[str]
    order: list[Any]
    def __init__(self, rargs: list[str]) -> None: ...

class OptionParser:
    ctx: Context | None
    allow_interspersed_args: bool
    ignore_unknown_options: bool
    _short_opt: dict[str, Option]
    _long_opt: dict[str, Option]
    _opt_prefixes: Set[str]
    _args: list[Argument]
    def __init__(self, ctx: Context | None = ...) -> None: ...
    def add_option(
        self,
        opts: Iterable[str],
        dest: str,
        action: str | None = ...,
        nargs: int = ...,
        const: Any | None = ...,
        obj: Any | None = ...,
    ) -> None: ...
    def add_argument(self, dest: str, nargs: int = ..., obj: Any | None = ...) -> None: ...
    def parse_args(self, args: list[str]) -> tuple[dict[str, Any], list[str], list[Any]]: ...
