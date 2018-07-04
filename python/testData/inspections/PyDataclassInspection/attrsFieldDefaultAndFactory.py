import attr


@attr.dataclass
class E1:
    a: int = attr.ib(default=1)
    b: int = attr.ib(default=attr.Factory(int))
    c: int = attr.ib(factory=int)
    d: int = attr.ib<error descr="Cannot specify both 'default' and 'factory'">(default=1, factory=int)</error>
    e: int = attr.ib<error descr="Cannot specify both 'default' and 'factory'">(default=attr.Factory(int), factory=int)</error>
    f: int = attr.ib(default=attr.NOTHING, factory=int)
    g: int = attr.ib(default=1, factory=None)