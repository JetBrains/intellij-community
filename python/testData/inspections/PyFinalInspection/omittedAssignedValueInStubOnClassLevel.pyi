from typing_extensions import Final

class A:
    a: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">Final</warning>
    b: Final[int]
    c: int