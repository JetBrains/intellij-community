from attr import s, ib, assoc, evolve


@s(auto_attribs=True)
class A:
    a: int
    b: str = "str"


assoc(A(1), <arg1>)
evolve(A(1), <arg2>)


@s(auto_attribs=True)
class B:
    a: int
    b: str = ib(default="str", init=False)


assoc(B(1), <arg3>)
evolve(B(1), <arg4>)


class C:
    pass


assoc(C(), <arg5>)
evolve(C(), <arg6>)