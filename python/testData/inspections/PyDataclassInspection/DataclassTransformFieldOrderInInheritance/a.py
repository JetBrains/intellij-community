from bases import A1, A3, A41, A42
from decorator import my_dataclass


@my_dataclass()
class <error descr="Non-default argument(s) follows default argument(s) defined in 'A1'">B1</error>(A1):
    y1: str
    y2: str = "1"


@my_dataclass()
class B3(A3):
    y1: str


@my_dataclass()
class B4<error descr="Inherited non-default argument(s) defined in A41 follows inherited default argument defined in A42">(A41, A42)</error>:
    pass


