from typing_extensions import Final

class A:
    def __init__(self):
        self.a: Final[int] = 1

class B:
    b: Final[int]

    def __init__(self):
        self.b = 1