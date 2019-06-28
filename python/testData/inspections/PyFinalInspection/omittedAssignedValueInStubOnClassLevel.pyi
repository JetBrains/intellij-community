from typing_extensions import Final

class A:
    a: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">Final</warning>
    b: Final[int]
    c: int

MY_FINAL = Final
MY_FINAL_INT = Final[int]

class B:
    с: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">MY_FINAL</warning>
    d: MY_FINAL_INT