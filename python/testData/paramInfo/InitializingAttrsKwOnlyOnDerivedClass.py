import attr


@attr.s
class Base1:
    a = attr.ib(type=int)

@attr.s(kw_only=True)
class Derived1(Base1):
    b = attr.ib(type=int)

Derived1(<arg1>)


@attr.s
class Base2:
    a = attr.ib(type=int)

@attr.s(kw_only=True)
class Derived2(Base2):
    b = attr.ib(type=int, default=1)

Derived2(<arg2>) # non-working case


@attr.s
class Base3:
    a = attr.ib(type=int, default=1)

@attr.s(kw_only=True)
class Derived3(Base3):
    b = attr.ib(type=int)

Derived3(<arg3>)


@attr.s
class Base4:
    a = attr.ib(type=int, default=1)

@attr.s(kw_only=True)
class Derived4(Base4):
    b = attr.ib(type=int, default=1)

Derived4(<arg4>)