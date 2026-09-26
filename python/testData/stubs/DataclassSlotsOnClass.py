from dataclasses import dataclass

@dataclass(slots=True)
class Foo1:
    bar: str = "hello"

@dataclass(slots=False)
class Foo2:
    bar: str = "hello"

@dataclass
class Foo3:
    bar: str = "hello"