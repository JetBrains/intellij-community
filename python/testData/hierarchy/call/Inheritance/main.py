
class A(object):
    def target_func(self):
        pass


class B(A):
    pass


class C(object):
    def func(self, a):
        a.target_func()


def foo(b):
    f = b.target_func


def fuu(b):
    b.target_func()


def bar(f):
    f()


b = B()
foo(b)
bar(b.target_<caret>func)