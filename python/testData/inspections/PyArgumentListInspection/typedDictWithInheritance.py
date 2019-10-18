from typing import TypedDict


class X(TypedDict):
    x: int


class Y(TypedDict, total=False):
    y: str


class XYZ(X, Y):
    z: bool


xyz = XYZ(z=True<warning descr="Parameter 'x' unfilled">)</warning>
