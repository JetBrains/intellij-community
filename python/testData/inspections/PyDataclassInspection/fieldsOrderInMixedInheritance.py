import attr
import dataclasses

class A1:
    x1: int
    x2: int = 1

@dataclasses.dataclass
class B1(A1):
    y1: str
    y2: str = "1"

class A2:
    x1: int
    x2: int = 1

@attr.dataclass
class B2(A2):
    y1: str
    y2: str = "1"

@dataclasses.dataclass
class A3:
    x1: int
    x2: int = 1

class B3(A3):
    y1: str
    y2: str = "1"

@attr.dataclass
class A4:
    x1: int
    x2: int = 1

class B4(A4):
    y1: str
    y2: str = "1"
