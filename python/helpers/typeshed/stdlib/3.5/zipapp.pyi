# Stubs for zipapp (Python 3.5+)

from pathlib import Path
from typing import BinaryIO, Optional, Union

_Path = Union[str, Path, BinaryIO]

class ZipAppError(Exception): ...

def create_archive(source: _Path, target: Optional[_Path] = ..., interpreter: Optional[str] = ..., main: Optional[str] = ...) -> None: ...
def get_interpreter(archive: _Path) -> str: ...
