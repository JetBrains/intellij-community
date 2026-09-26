import os
from typing import Any, Final
from typing_extensions import Self

SCHEMES: Final[dict[str, str]]
ARCHIVESCHEMES: Final[type[set[Any]]]
CURLSCHEMES: Final[set[str]]
REMOTESCHEMES: Final[set[str]]

class _Path:
    def as_vsi(self) -> str: ...

class _ParsedPath(_Path):
    path: str
    archive: str | None
    scheme: str | None
    def __init__(self, path: str, archive: str | None, scheme: str | None) -> None: ...
    @classmethod
    def from_uri(cls, uri: str) -> Self: ...
    @property
    def name(self) -> str: ...
    @property
    def is_remote(self) -> bool: ...
    @property
    def is_local(self) -> bool: ...

class _UnparsedPath(_Path):
    path: str
    def __init__(self, path: str) -> None: ...
    @property
    def name(self) -> str: ...

def _parse_path(path: str | os.PathLike[str] | _Path) -> _ParsedPath | _UnparsedPath: ...
def _vsi_path(path: _Path) -> str: ...
