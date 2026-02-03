from typing_extensions import Final
from b import A

class B(A):
    def __init__(self):
        super().__init__()
        <warning descr="'A.a' is 'Final' and could not be overridden">self.a</warning>: Final[str] = "2"

class C(A):
    <warning descr="'A.a' is 'Final' and could not be overridden">a</warning>: Final[str]

    def __init__(self):
        super().__init__()
        self.a = "3"