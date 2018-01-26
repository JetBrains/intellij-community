import dataclasses

@dataclasses.dataclass
class A1:
    x: int = 0
    y: dataclasses.InitVar[int] = 1

    def __post_init__(self, y: int):