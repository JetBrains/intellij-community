import attr
import attrs


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


@attrs.define
class B:
    a: int = attrs.field(default=1)
    b: int = attrs.field(default=attr.Factory(int))
    c: int = attrs.field()
    d: int = attrs.field(init=False)
    e: int = attrs.field(init=False)
    f: int = attrs.field(init=False)
    g: int = attrs.field(default=attrs.NOTHING)
    h: int = attrs.field(factory=int)
    i: int = attrs.field(factory=None)