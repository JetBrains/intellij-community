import dataclasses

@dataclasses.dataclass
class A1:
    <error descr="Attribute 'a' lacks a type annotation">a</error> = dataclasses.field()
    b = 1
    c: int = 1
