from typing import Any, Iterable, Union

__version__: str

_Argv = Union[Iterable[str], str]

def docopt(
    doc: str, argv: _Argv | None = ..., help: bool = ..., version: Any | None = ..., options_first: bool = ...
) -> dict[str, Any]: ...  # Really should be dict[str, str | bool]
