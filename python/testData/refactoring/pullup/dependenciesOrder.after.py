class DataHolder:
    VAR = 1

class Parent:
    A_FIELD = DataHolder.VAR
    CLASS_FIELD = 42
    ANOTHER_CLASS_FIELD = CLASS_FIELD
    BOO = 12
    FIELD = BOO

    def __init__(self):
        self.d = Parent.BOO
        self.c = 1
        self.b = self.c

    @staticmethod
    def foo():
        return "A"

    SOME_VAR = foo()


class Child(Parent):  # Try to pull members up

    def __init__(self):
        super(Child, self).__init__()
        self.a = 12
        i = 1