from file_1 import A

class B(A):
    def target_func(self, p):
        p.another_func()


class C(object):
    def func(self, a):
        a.target_func(A())

def foo(b):
    f = b.target_func
    b.target_func


def fuu(b):
    b.target_func(A())


def bar(f):
    f(A())


a = A()
b = B()
foo(b)
fuu(b)
bar(a.target_func)
b.target_<caret>func(A())