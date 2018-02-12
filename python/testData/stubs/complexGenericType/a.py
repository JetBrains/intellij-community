from mod import Base, T1


class MyClass(Base[T1, None]):
    pass


x = MyClass(42)
expr = x.m('foo')

print(expr)
