import attr


@attr.s(kw_only=True)
class Base1:
    a = attr.ib(type=int)

@attr.s
class Derived1(Base1):
    b = attr.ib(type=int)

Derived1(<arg1>)


@attr.s(kw_only=True)
class Base2:
    a = attr.ib(type=int)

@attr.s
class Derived2(Base2):
    b = attr.ib(type=int, default=1)

Derived2(<arg2>)


@attr.s(kw_only=True)
class Base3:
    a = attr.ib(type=int, default=1)

@attr.s
class Derived3(Base3):
    b = attr.ib(type=int)

Derived3(<arg3>)


@attr.s(kw_only=True)
class Base4:
    a = attr.ib(type=int, default=1)

@attr.s
class Derived4(Base4):
    b = attr.ib(type=int, default=1)

Derived4(<arg4>)