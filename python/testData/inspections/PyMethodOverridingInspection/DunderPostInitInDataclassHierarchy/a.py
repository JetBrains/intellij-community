import dataclasses

@dataclasses.dataclass
class A1:
    a: int
    b: dataclasses.InitVar[str]

    def __post_init__(self, b: str):
        print(f"b: {b}")

@dataclasses.dataclass
class B1(A1):
    c: dataclasses.InitVar[int]

    def __post_init__(self, b: str, c: int):
        super(B1, self).__post_init__(b)
        print(f"c: {c}")