import dataclasses

@dataclasses.dataclass
class Base:
    a1: int = 0
    a2: dataclasses.InitVar[str]

@dataclasses.dataclass
class Derived(Base):
    a3 = ""
    a4: dataclasses.InitVar[bool]
    def __post_init__(self, a2: str, a4: bool):