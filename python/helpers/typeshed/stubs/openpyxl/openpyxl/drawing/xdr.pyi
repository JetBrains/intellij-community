from typing import Any

from .geometry import Point2D, PositiveSize2D, Transform2D

class XDRPoint2D(Point2D):
    namespace: Any
    x: Any
    y: Any

class XDRPositiveSize2D(PositiveSize2D):
    namespace: Any
    cx: Any
    cy: Any

class XDRTransform2D(Transform2D):
    namespace: Any
    rot: Any
    flipH: Any
    flipV: Any
    off: Any
    ext: Any
    chOff: Any
    chExt: Any
