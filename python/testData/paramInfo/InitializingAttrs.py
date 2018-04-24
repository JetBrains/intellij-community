import attr

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

