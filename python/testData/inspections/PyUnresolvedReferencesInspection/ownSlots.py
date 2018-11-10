def access1():
    class B(object):
        __slots__ = ['foo']

    b = B()
    print(b.<warning descr="Unresolved attribute reference 'baz' for class 'B'">baz</warning>)
    print(b.foo)


def assign1():
    class B(object):
        __slots__ = ['foo']

    b = B()
    b.<warning descr="'B' object has no attribute 'bar'">bar</warning> = 1
    b.foo = 1


def access2():
    class A:
        __slots__ = ['foo']

    a = A()
    print(a.<warning descr="Unresolved attribute reference 'foo' for class 'A'">foo</warning>)
    print(a.<warning descr="Unresolved attribute reference 'bar' for class 'A'">bar</warning>)


def assign2():
    class A:
        __slots__ = ['foo']

    a = A()
    a.foo = 1
    a.bar = 1