import attr
@attr.s(kw_only=True)
class Foo1:
    bar = attr.ib(type=str)
@attr.s(kw_only=False)
class Foo2:
    bar = attr.ib(type=str)
@attr.s
class Foo3:
    bar = attr.ib(type=str)