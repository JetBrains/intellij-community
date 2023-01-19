from _typeshed import Self
from collections.abc import Callable, Iterable, Sequence
from typing import Any
from typing_extensions import Literal, TypeAlias

from .Image import Image

_FilterArgs: TypeAlias = tuple[Sequence[int], int, int, Sequence[int]]

# filter image parameters below are the C images, i.e. Image().im.

class Filter: ...
class MultibandFilter(Filter): ...

class BuiltinFilter(MultibandFilter):
    def filter(self, image) -> Image: ...

class Kernel(BuiltinFilter):
    name: str
    filterargs: _FilterArgs
    def __init__(self, size: Sequence[int], kernel: Sequence[int], scale: Any | None = ..., offset: int = ...) -> None: ...

class RankFilter(Filter):
    name: str
    size: int
    rank: int
    def __init__(self, size: int, rank: int) -> None: ...
    def filter(self, image) -> Image: ...

class MedianFilter(RankFilter):
    name: str
    size: int
    rank: int
    def __init__(self, size: int = ...) -> None: ...

class MinFilter(RankFilter):
    name: str
    size: int
    rank: int
    def __init__(self, size: int = ...) -> None: ...

class MaxFilter(RankFilter):
    name: str
    size: int
    rank: int
    def __init__(self, size: int = ...) -> None: ...

class ModeFilter(Filter):
    name: str
    size: int
    def __init__(self, size: int = ...) -> None: ...
    def filter(self, image) -> Image: ...

class GaussianBlur(MultibandFilter):
    name: str
    radius: float
    def __init__(self, radius: float = ...) -> None: ...
    def filter(self, image) -> Image: ...

class BoxBlur(MultibandFilter):
    name: str
    radius: float
    def __init__(self, radius: float) -> None: ...
    def filter(self, image) -> Image: ...

class UnsharpMask(MultibandFilter):
    name: str
    radius: float
    percent: int
    threshold: int
    def __init__(self, radius: float = ..., percent: int = ..., threshold: int = ...) -> None: ...
    def filter(self, image) -> Image: ...

class BLUR(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class CONTOUR(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class DETAIL(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class EDGE_ENHANCE(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class EDGE_ENHANCE_MORE(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class EMBOSS(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class FIND_EDGES(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class SHARPEN(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class SMOOTH(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class SMOOTH_MORE(BuiltinFilter):
    name: str
    filterargs: _FilterArgs

class Color3DLUT(MultibandFilter):
    name: str
    size: list[int]
    channels: int
    mode: str | None
    table: Any
    def __init__(
        self, size: int | Iterable[int], table, channels: int = ..., target_mode: str | None = ..., **kwargs
    ) -> None: ...
    @classmethod
    def generate(
        cls: type[Self],
        size: int | tuple[int, int, int],
        callback: Callable[[float, float, float], Iterable[float]],
        channels: int = ...,
        target_mode: str | None = ...,
    ) -> Self: ...
    def transform(
        self: Self,
        callback: Callable[..., Iterable[float]],
        with_normals: bool = ...,
        channels: Literal[3, 4] | None = ...,
        target_mode: Any | None = ...,
    ) -> Self: ...
    def filter(self, image) -> Image: ...
