from dataclasses import field, InitVar, replace
from pydantic.dataclasses import dataclass

@dataclass
class A:
    a: int
    b: str = "str"


replace(A(1), <arg1>)


@dataclass
class B:
    a: int
    b: str = field(default="str", init=False)


replace(B(1), <arg2>)


@dataclass
class C:
    a: int
    b: InitVar[str] = "str"


replace(C(1), <arg3>)


class D:
    pass


replace(D(), <arg4>)