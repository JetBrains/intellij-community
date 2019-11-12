import attr
import dataclasses
import pydantic

@dataclasses.dataclass
class A1:
    x1: int
    x2: int = 1

@dataclasses.dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'A1'">B1</error>(A1):
    y1: str
    y2: str = "1"

@attr.dataclass
class A2:
    x1: int
    x2: int = 1

@attr.dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'A2'">B2</error>(A2):
    y1: str
    y2: str = "1"

@dataclasses.dataclass
class A3:
    x1: int

@dataclasses.dataclass
class B3(A3):
    y1: str

@dataclasses.dataclass
class A41:
    field1: int

@dataclasses.dataclass
class A42:
    field2: str = "1"

@dataclasses.dataclass
class B4<error descr="Inherited non-default argument(s) defined in A41 follows inherited default argument defined in A42">(A41, A42)</error>:
    pass

@attr.dataclass
class A51:
    field1: int

@attr.dataclass
class A52:
    field2: str = "1"

@attr.dataclass
class B5(A51, A52):
    pass


@pydantic.dataclasses.dataclass
class A6:
    x1: int
    x2: int = 1

@pydantic.dataclasses.dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'A6'">B6</error>(A6):
    y1: str
    y2: str = "1"

@pydantic.dataclasses.dataclass
class A7:
    x1: int

@pydantic.dataclasses.dataclass
class B7(A7):
    y1: str

@pydantic.dataclasses.dataclass
class A81:
    field1: int

@pydantic.dataclasses.dataclass
class A82:
    field2: str = "1"

@pydantic.dataclasses.dataclass
class B8<error descr="Inherited non-default argument(s) defined in A81 follows inherited default argument defined in A82">(A81, A82)</error>:
    pass
