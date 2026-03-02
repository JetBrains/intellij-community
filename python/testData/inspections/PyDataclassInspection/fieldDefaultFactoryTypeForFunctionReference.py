# testFieldDefaultFactoryTypeForSimpleReference.py
from dataclasses import dataclass, field
from typing import Callable

def make_str() -> str:
    return "hello"

@dataclass
class A:
    x: int = <warning descr="Type mismatch: field annotation is 'int', but default_factory returns 'str'">field(default_factory=make_str)</warning>