from typing_extensions import Final
from b import A

class B(A):
    <warning descr="'A.a' is 'Final' and cannot be overridden">a</warning>: Final[str] = "3"
