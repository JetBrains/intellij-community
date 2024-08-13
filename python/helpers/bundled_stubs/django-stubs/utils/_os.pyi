import os
from pathlib import Path

from typing_extensions import TypeAlias

_PathCompatible: TypeAlias = str | os.PathLike[str]

def safe_join(base: _PathCompatible, *paths: _PathCompatible) -> str: ...
def symlinks_supported() -> bool: ...
def to_path(value: Path | str) -> Path: ...
