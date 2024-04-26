import dataclasses
from _typeshed import Incomplete
from dataclasses import dataclass

from .drawing import DeviceGray, DeviceRGB, Number
from .enums import TextEmphasis

@dataclass
class FontFace:
    family: str | None
    emphasis: TextEmphasis | None
    size_pt: int | None
    color: int | tuple[Number, Number, Number] | DeviceGray | DeviceRGB | None
    fill_color: int | tuple[Number, Number, Number] | DeviceGray | DeviceRGB | None

    def __init__(
        self,
        family: str | None = None,
        emphasis: Incomplete | None = None,
        size_pt: int | None = None,
        color: int | tuple[Number, Number, Number] | DeviceGray | DeviceRGB | None = None,
        fill_color: int | tuple[Number, Number, Number] | DeviceGray | DeviceRGB | None = None,
    ) -> None: ...

    replace = dataclasses.replace

COURIER_FONT: dict[str, int]
CORE_FONTS_CHARWIDTHS: dict[str, dict[str, int]]
