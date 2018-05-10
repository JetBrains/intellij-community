import attr


@attr.dataclass
class A:
    a: int = attr.ib(default=1)
    b: int = attr.ib(default=attr.Factory(int))
    c: int = attr.ib()
    d: int = attr.ib(init=False)
    e: int = attr.attr(init=False)
    f: int = attr.attrib(init=False)
    g: int = attr.ib(default=attr.NOTHING)
    h: int = attr.ib(factory=int)
    i: int = attr.ib(factory=None)