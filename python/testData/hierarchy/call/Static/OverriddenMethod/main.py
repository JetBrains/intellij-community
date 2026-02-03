from file_1 import A


class B(A):
    def target_func(self, p):
        p.another_func()


class C(object):
    def func1(self, a):
        a.target_func(A())

    def func2(self):
        a = A()
        b = B()
        a.target_func(b)


def bar1(a):
    a.target_func(a)


def bar2(a, b):
    atf, btf = a.target_func, b.target_func


bar1(A())
bar2(A(), B())
B().target_<caret>func(A())