from typing import Any
from typing_extensions import Literal

_ImageFilter = Literal["AUTO", "FlateDecode", "DCTDecode", "JPXDecode"]

SUPPORTED_IMAGE_FILTERS: tuple[_ImageFilter, ...]

def load_image(filename): ...

# Returned dict could be typed as a TypedDict.
def get_img_info(img, image_filter: _ImageFilter = ..., dims: Any | None = ...) -> dict[str, Any]: ...
