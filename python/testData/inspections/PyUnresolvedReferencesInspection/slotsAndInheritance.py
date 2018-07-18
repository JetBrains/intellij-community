def access1():
    class B(object):
        __slots__ = ['foo']

    class C(B):
        pass

    c = C()
    print(c.<warning descr="Unresolved attribute reference 'bar' for class 'C'">bar</warning>)
    print(c.foo)


def assign1():
    class B(object):
        __slots__ = ['foo']

    class C(B):
        pass

    c = C()
    c.bar = 1
    c.foo = 1


def access2():
    class A(object):
        __slots__ = ['foo']

    class D(A):
        __slots__ = ['bar']

    d = D()
    print(d.foo)
    print(d.bar)
    print(d.<warning descr="Unresolved attribute reference 'baz' for class 'D'">baz</warning>)


def assign2():
    class A(object):
        __slots__ = ['foo']

    class D(A):
        __slots__ = ['bar']

    d = D()
    d.foo = 1
    d.bar = 1
    d.<warning descr="'D' object has no attribute 'baz'">baz</warning> = 1