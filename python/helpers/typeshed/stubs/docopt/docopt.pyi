from typing import Any, Iterable, Optional, Union

__version__: str

_Argv = Union[Iterable[str], str]

def docopt(
    doc: str, argv: Optional[_Argv] = ..., help: bool = ..., version: Optional[Any] = ..., options_first: bool = ...
) -> dict[str, Any]: ...  # Really should be dict[str, Union[str, bool]]
