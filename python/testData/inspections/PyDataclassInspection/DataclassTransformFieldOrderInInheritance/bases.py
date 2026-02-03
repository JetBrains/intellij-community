from decorator import my_dataclass


@my_dataclass()
class A1:
    x1: int
    x2: int = 1


@my_dataclass()
class A3:
    x1: int


@my_dataclass()
class A41:
    field1: int


@my_dataclass()
class A42:
    field2: str = "1"
