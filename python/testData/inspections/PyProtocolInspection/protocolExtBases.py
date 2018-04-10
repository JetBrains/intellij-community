from typing_extensions import Protocol


class MyProto1(Protocol):
    pass


class MyProto2(Protocol):
    pass


class A:
    pass


class B(A, MyProto1):
    pass


class C(MyProto1, MyProto2):
    pass


class <warning descr="All bases of a protocol must be protocols">D</warning>(A, MyProto1, Protocol):
    pass


class E(MyProto1, MyProto2, Protocol):
    pass
