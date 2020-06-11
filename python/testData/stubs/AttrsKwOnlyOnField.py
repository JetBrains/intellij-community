import attr
@attr.s
class Foo:
    bar1 = attr.ib(type=str)
    bar2 = attr.ib(type=str, kw_only=True)
    bar3 = attr.ib(type=str, kw_only=False)