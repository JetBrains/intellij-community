from typing import Protocol


class VariableExample(Protocol):
    name: str
    value: int = 0


class VariableExampleImpl1:
    def __init__(self, name: str, value: int) -> None:
        self.name = name
        self.value = value


class VariableExampleImpl2(VariableExample):
    def __init__(self, name: str) -> None:
        self.name = name


class VariableExampleImpl3:
    def __init__(self, name: str) -> None:
        self.name = name


def example(e: VariableExample) -> None:
    print(e.name)
    print(e.value)


example(VariableExampleImpl1("1", 1))
example(VariableExampleImpl2("1"))
example(<warning descr="Expected type 'VariableExample', got 'VariableExampleImpl3' instead">VariableExampleImpl3("1")</warning>)
