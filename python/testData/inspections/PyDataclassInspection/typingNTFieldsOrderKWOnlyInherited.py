from dataclasses import dataclass, field, KW_ONLY


@dataclass()
class A:
    a: int

    _: KW_ONLY
    b: int = field(default=0)


@dataclass()
class B(A):
    c: int