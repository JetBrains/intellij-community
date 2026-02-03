import attr
import attrs


@attr.s
class AttrS:
    x = attr.ib(type=int)
    y = attr.ib(type=str)

AttrS(<arg1>)


@attr.attrs
class AttrAttrs:
    x = attr.attrib(type=int)
    y = attr.attrib(type=str)

AttrAttrs(<arg2>)


@attr.attributes
class AttrAttributes:
    x = attr.attrib(type=int)
    y = attr.attrib(type=str)

AttrAttributes(<arg3>)


@attr.dataclass
class AttrDataclass:
    x: int
    y: str

AttrDataclass(<arg4>)


@attr.define
class AttrDefine:
    x: int
    y: str

AttrDefine(<arg5>)


@attr.mutable
class AttrMutable:
    x: int
    y: str

AttrMutable(<arg6>)


@attr.frozen
class AttrFrozen:
    x: int
    y: str

AttrFrozen(<arg7>)


@attrs.define
class AttrsDefine:
    x: int
    y: str

AttrsDefine(<arg8>)


@attrs.mutable
class AttrsMutable:
    x: int
    y: str

AttrsMutable(<arg9>)


@attrs.frozen
class AttrsFrozen:
    x: int
    y: str

AttrsFrozen(<arg10>)


@attr.define
class AttrsDefineWithAttrsField:
    x = attrs.field()
    y = attrs.field()

AttrsDefineWithAttrsField(<arg11>)