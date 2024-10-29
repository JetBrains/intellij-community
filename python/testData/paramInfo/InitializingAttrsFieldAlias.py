import attr
import attrs

@attr.s
class C1:
    x = attr.ib(alias="foo")
    _bar = attr.ib()
    z = attr.ib(alias='baz')

C1(<arg1>)

@attrs.define
class C2:
    x = attrs.field(alias="foo")
    _bar = attrs.field()
    z = attrs.field(alias='baz')

C2(<arg2>)