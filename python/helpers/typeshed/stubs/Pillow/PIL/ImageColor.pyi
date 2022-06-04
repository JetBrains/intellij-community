from typing import Union

_RGB = Union[tuple[int, int, int], tuple[int, int, int, int]]
_Ink = Union[str, int, _RGB]
_GreyScale = tuple[int, int]

def getrgb(color: _Ink) -> _RGB: ...
def getcolor(color: _Ink, mode: str) -> _RGB | _GreyScale: ...

colormap: dict[str, str]
