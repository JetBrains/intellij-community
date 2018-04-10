import dataclasses

@dataclasses.dataclass
class A:
    a: int
    <warning descr="Attribute 'b' is useless until '__post_init__' is declared">b</warning>: dataclasses.InitVar[str]
