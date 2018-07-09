import dataclasses

@dataclasses.dataclass
class A:
    a: int
    b: dataclasses.InitVar[str]

    def __post_init__(self, b: str):
        pass


a = A(1, "a")
print(a.a)
print(a.<warning descr="'A' object could have no attribute 'b' because it is declared as init-only">b</warning>)
