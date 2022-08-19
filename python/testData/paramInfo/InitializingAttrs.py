import attr
import attrs

@attr.s
class A1:
    x = attr.ib()
    y = attr.ib()
    z = attr.ib(default=0)

A1(<arg1>)


@attr.s
class A2:
    x = attr.ib()
    y = attr.ib(init=True)
    z = attr.ib(default=0)

A2(<arg2>)


@attr.s
class A3:
    x = attr.ib()
    y = attr.ib(init=False)
    z = attr.ib(default=0)

A3(<arg3>)


@attr.s
class B1:
    x = attr.ib()
    y = attr.ib()
    z = attr.ib(default=attr.Factory(list))

B1(<arg4>)


@attr.attrs
class C1:
    x = attr.ib()
    y = attr.attr(default=0)

C1(<arg5>)


@attr.attributes
class C2:
    x = attr.attr()
    y = attr.attrib(default="0")

C2(<arg6>)


@attr.s
class F1:
    x = attr.ib()

    @x.default
    def __init_x__(self):
        return 1

F1(<arg7>)


@attrs.define
class B2:
    x = attrs.field()
    y = attrs.field()
    z = attrs.field(default=attrs.Factory(list))

B2(<arg8>)
