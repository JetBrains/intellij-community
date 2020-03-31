from typing_extensions import Final

a: Final[int]
b: <warning descr="If assigned value is omitted, there should be an explicit type argument to 'Final'">Final</warning>
<warning descr="'b' is 'Final' and could not be reassigned">b</warning> = "10"
c: Final[str] = "10"
d: int