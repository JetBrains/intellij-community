from dataclasses import dataclass, field
from typing import Callable

def make_factory() -> Callable[[], str]:
    def inner() -> str:
        return "hello"
    return inner

@dataclass
class B:
    y: int = <warning descr="Type mismatch: field annotation is 'int', but default_factory returns 'str'">field(default_factory=make_factory())</warning>