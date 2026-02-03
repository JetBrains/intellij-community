
from typing import Callable, Literal, Union
from os import PathLike


StrPath = Union[str, PathLike[str]]  # stable
BytesPath = Union[bytes, PathLike[bytes]]  # stable
StrOrBytesPath = Union[str, bytes, PathLike[str], PathLike[bytes]]  # stable
_OpenFile = Union[StrOrBytesPath, int]
_Opener = Callable[[str, int], int]

OpenTextModeWriting = Literal["w", "wt", "tw", "a", "at", "ta", "x", "xt", "tx"]
OpenTextModeReading = Literal["r", "rt", "tr", "U"]
OpenTextMode = Union[OpenTextModeWriting, OpenTextModeReading]

def open(
        file: _OpenFile,
        mode: OpenTextMode = ...,
        buffering: int = ...,
):
    pass


open(<arg1>, "rb")
open("my_file.py", <arg2>)
