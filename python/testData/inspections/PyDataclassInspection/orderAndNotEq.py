import dataclasses


@dataclasses.dataclass(<error descr="eq must be true if order is true">eq=False</error>, order=True)
class A1:
    x: int


@dataclasses.dataclass(eq=False)
class A2:
    x: int


@dataclasses.dataclass(eq=False, order=False)
class A3:
    x: int