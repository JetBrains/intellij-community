import abc
from abc import abstractmethod
from typing import Callable


def decorator(f):
    return f
    
def typed_decorator(f) -> Callable[..., int]:
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
        
    @typed_decorator
    def baz2(self):
        pass

    def quux(self):
        pass

    def test(self):
        a = self.foo()
        b = self.bar()
        <weak_warning descr="Function 'baz' doesn't return anything">c1 = self.baz()</weak_warning>
        c2 = self.baz2()
        <weak_warning descr="Function 'quux' doesn't return anything">d = self.quux()</weak_warning>
