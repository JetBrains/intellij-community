class A:
    def foo(self):
        pass


class B:
    def bar(self):
        pass


var = object()
if isinstance(var, A) and var.foo():
    pass


if isinstance(var, A):
    if isinstance(var, B) or var.<warning descr="Unresolved attribute reference 'bar' for class 'A'">bar</warning>():
        pass