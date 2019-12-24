import attr


@attr.s
class Base1:
    a = attr.ib(type=int)

@attr.s(kw_only=True)
class Derived1(Base1):
    a = attr.ib(type=int)

Derived1(<arg1>)


@attr.s(kw_only=True)
class Base2:
    a = attr.ib(type=int)

@attr.s
class Derived2(Base2):
    a = attr.ib(type=int)

Derived2(<arg2>)


@attr.s(kw_only=True)
class Base3:
    a = attr.ib(type=int)

@attr.s(kw_only=True)
class Derived3(Base3):
    a = attr.ib(type=int)

Derived3(<arg3>)