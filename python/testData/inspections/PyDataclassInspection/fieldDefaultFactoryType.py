from dataclasses import dataclass, field

@dataclass
class E:
    a: int = <warning descr="Type mismatch: field annotation is 'int', but default_factory returns 'str'">field(default_factory=str)</warning>