from dataclasses import dataclass, field, InitVar, replace


@dataclass
class A:
    a: int
    b: str = "str"


replace(A(1))
replace(A(1), a=1)
replace(A(1), a=1, b="abc")
replace(A(1), a=1, b="abc", <warning descr="Unexpected argument">c=2</warning>)


@dataclass
class B:
    a: int
    b: str = field(default="str", init=False)


replace(B(1))
replace(B(1), a=1)
replace(B(1), a=1, <warning descr="Unexpected argument">b="abc"</warning>)
replace(B(1), a=1, <warning descr="Unexpected argument">b="abc"</warning>, <warning descr="Unexpected argument">c=2</warning>)


@dataclass
class C:
    a: int
    b: InitVar[str] = "str"


replace(C(1))
replace(C(1), a=1)
replace(C(1), a=1, b="abc")
replace(C(1), a=1, b="abc", <warning descr="Unexpected argument">c=2</warning>)


class D:
    pass


replace(D())
replace(D(), a=1, b=2)