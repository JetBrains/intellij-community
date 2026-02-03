import dataclasses

@dataclasses.dataclass
class A1:
    x: int = 0
    y: dataclasses.InitVar[int] = 1

    def __post<caret>