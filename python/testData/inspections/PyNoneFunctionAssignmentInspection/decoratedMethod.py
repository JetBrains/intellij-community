import abc
from abc import abstractmethod


def decorator(f):
    return f


class C(object):
    __metaclass__ = abc.ABCMeta

    @abstractmethod
    def foo(self):
        pass

    @abc.abstractmethod
    def bar(self):
        pass

    @decorator
    def baz(self):
        pass

    def quux(self):
        pass

    def test(self):
        a = self.foo()
        b = self.bar()
        c = self.baz()
        <weak_warning descr="Function 'quux' doesn't return anything">d = self.quux()</weak_warning>
