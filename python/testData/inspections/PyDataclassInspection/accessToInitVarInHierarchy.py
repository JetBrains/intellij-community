import dataclasses

@dataclasses.dataclass
class A1:
    a: int
    b: dataclasses.InitVar[str]

    def __post_init__(self, b: str):
        pass


@dataclasses.dataclass
class B1(A1):
    pass


class C1(A1):
    pass


b1 = B1(1, "a")
print(b1.a)
print(b1.<warning descr="'B1' object could have no attribute 'b' because it is declared as init-only">b</warning>)
print(b1)


c1 = C1(1, "a")
print(c1.a)
print(c1.<warning descr="'C1' object could have no attribute 'b' because it is declared as init-only">b</warning>)
print(c1)
