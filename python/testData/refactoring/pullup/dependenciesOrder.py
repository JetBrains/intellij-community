class DataHolder:
    VAR = 1

class Parent:
    BOO = 12

    def __init__(self):
        self.c = 1


class Child(Parent):  # Try to pull members up
    CLASS_FIELD = 42
    ANOTHER_CLASS_FIELD = CLASS_FIELD
    FIELD = Parent.BOO
    A_FIELD = DataHolder.VAR

    @staticmethod
    def foo():
        return "A"

    SOME_VAR = foo()

    def __init__(self):
        super(Child, self).__init__()
        self.a = 12
        self.b = self.c
        self.d = Parent.BOO
        i = 1