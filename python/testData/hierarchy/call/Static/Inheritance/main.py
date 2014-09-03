
class A(object):
    def target_func(self):
        pass


class B(A):
    pass


class C(object):
    def func(self, a):
        a.target_func()


def foo1(b):
    f = b.target_func


def foo2(b):
    b.target_func()


def bar1(*args):
    pass


def bar2(*args):
    pass


b = B()
foo1(b)
foo2(b)
bar1(b.target_<caret>func)
bar2(b.target_func())