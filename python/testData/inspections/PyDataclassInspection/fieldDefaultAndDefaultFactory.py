import dataclasses
from typing import ClassVar


@dataclasses.dataclass
class E1:
    a: int = dataclasses.field(default=1)
    b: int = dataclasses.field(default_factory=int)
    c: int = dataclasses.field<error descr="Cannot specify both 'default' and 'default_factory'">(default=1, default_factory=int)</error>
    d: ClassVar[int] = dataclasses.field(default_factory=<error descr="Field cannot have a default factory">int</error>)
    e: dataclasses.InitVar[int] = dataclasses.field(default_factory=<error descr="Field cannot have a default factory">int</error>)

    def __post_init__(self, e: int):
        pass