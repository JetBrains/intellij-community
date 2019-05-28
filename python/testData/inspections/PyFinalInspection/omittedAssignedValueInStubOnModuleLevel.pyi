from typing_extensions import Final

a: Final[int]
b: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">Final</warning>
b = "10"
c: Final[str] = "10"
d: int