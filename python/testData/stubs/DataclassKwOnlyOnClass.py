from dataclasses import dataclass, field

@dataclass(kw_only=True)
class Foo1:
    bar: str = field()

@dataclass(kw_only=False)
class Foo2:
    bar: str = field()

@dataclass
class Foo3:
    bar: str = field()