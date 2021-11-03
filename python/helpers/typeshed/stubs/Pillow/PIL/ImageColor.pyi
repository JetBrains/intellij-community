from typing import Tuple, Union

_RGB = Union[Tuple[int, int, int], Tuple[int, int, int, int]]
_Ink = Union[str, int, _RGB]
_GreyScale = Tuple[int, int]

def getrgb(color: _Ink) -> _RGB: ...
def getcolor(color: _Ink, mode: str) -> _RGB | _GreyScale: ...

colormap: dict[str, str]
