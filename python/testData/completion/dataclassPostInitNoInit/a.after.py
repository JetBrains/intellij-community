import dataclasses

@dataclasses.dataclass(init=False)
class A1:
    x: int = 0

    def __post