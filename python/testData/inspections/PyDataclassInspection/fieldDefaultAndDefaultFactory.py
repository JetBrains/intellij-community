import dataclasses

@dataclasses.dataclass
class E1:
    a: int = dataclasses.field(default=1)
    b: int = dataclasses.field(default_factory=int)
    c: int = dataclasses.field<error descr="cannot specify both default and default_factory">(default=1, default_factory=int)</error>