import abc


class A(metaclass=abc.ABCMeta):
    @abc.abstractproperty
    def foo(self):
        pass


class C(A):
    foo = 'bar'