import os
from typing import Any

from rasterio._typing import _OpenOption

def exists(path: str | os.PathLike[str]) -> bool: ...
def copy(
    # An open dataset handle (DatasetReader / DatasetWriter / DatasetBase) or a path.
    src: str | os.PathLike[str] | Any,
    dst: str | os.PathLike[str],
    driver: str | None = None,
    strict: bool = True,
    **creation_options: _OpenOption,
) -> None: ...
def copyfiles(src: str | os.PathLike[str], dst: str | os.PathLike[str]) -> None: ...
def delete(path: str | os.PathLike[str], driver: str | None = None) -> None: ...
