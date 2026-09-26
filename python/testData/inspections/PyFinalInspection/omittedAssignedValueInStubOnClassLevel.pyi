from typing_extensions import Final

class A:
    a: <warning descr="If the assigned value is omitted, an explicit type argument for 'Final' is required">Final</warning>
    b: Final[int]
    c: int