from _typeshed import Incomplete
from typing import Final

__version__: Final[str]

class PDFImage:
    image: Incomplete
    x: Incomplete
    y: Incomplete
    width: Incomplete
    height: Incomplete
    filename: Incomplete
    imageCaching: Incomplete
    colorSpace: str
    bitsPerComponent: int
    filters: Incomplete
    source: Incomplete
    def __init__(self, image, x, y, width=None, height=None, caching: int = 0) -> None: ...
    def jpg_imagedata(self): ...
    def cache_imagedata(self): ...
    def PIL_imagedata(self): ...
    def non_jpg_imagedata(self, image): ...
    imageData: Incomplete
    imgwidth: Incomplete
    imgheight: Incomplete
    def getImageData(self, preserveAspectRatio: bool = False) -> None: ...
    def drawInlineImage(
        self,
        canvas,
        preserveAspectRatio: bool = False,
        anchor: str = "sw",
        anchorAtXY: bool = False,
        showBoundary: bool = False,
        extraReturn=None,
    ): ...
    def format(self, document): ...
