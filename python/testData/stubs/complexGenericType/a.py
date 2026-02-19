from mod import Base, T1


class MyClass(Base[T1, None, str]):
    # Requires an explicit constructor due to PY-63565
    def __init__(self, x: T1):
        super().__init__(x)


x = MyClass(42)
expr = x.m('foo')

print(expr)
