from m1 import A


def f():
    a = A()
    <weak_warning descr="Access to a protected member _foo of a class">a._foo</weak_warning>()
    return <weak_warning descr="Access to a protected member _x of a class">a._x</weak_warning>

