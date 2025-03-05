# Example from PY-33917

class Foo:
    def __init_subclass__(cls, x):
        cls.x = x

class Bar(Foo, x = 1):
    pass