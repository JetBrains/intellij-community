from typing import Protocol


class NamedParam(Protocol):
    def __call__(self, arg: float) -> float:
        pass

class StarParam(Protocol):
    def __call__(self, *args: float) -> float:
        pass

def named_parameter(arg: float) -> float:
    pass

def named_parameter_wrong_type(arg: int) -> float:
    pass

def star_parameter(*args: float) -> float:
    pass

def star_parameter_wrong_type(*args: int) -> float:
    pass

foo0: NamedParam = named_parameter
foo1: NamedParam = <warning descr="Expected type 'NamedParam', got '(arg: int) -> float' instead">named_parameter_wrong_type</warning>
foo2: StarParam = star_parameter
foo3: StarParam = <warning descr="Expected type 'StarParam', got '(args: tuple[int, ...]) -> float' instead">star_parameter_wrong_type</warning>
foo4: StarParam = <warning descr="Expected type 'StarParam', got '(arg: float) -> float' instead">named_parameter</warning>