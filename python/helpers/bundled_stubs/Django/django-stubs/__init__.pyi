from typing import Literal

from .utils.version import get_version as get_version

VERSION: tuple[int, int, int, Literal["alpha", "beta", "rc", "final"], int]
__version__: str

def setup(set_prefix: bool = ...) -> None: ...
