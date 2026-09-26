from typing_extensions import Final

a: Final[int]
b: <warning descr="If the assigned value is omitted, an explicit type argument for 'Final' is required">Final</warning>
<warning descr="'b' is 'Final' and cannot be reassigned">b</warning> = "10"
c: Final[str] = "10"
d: int