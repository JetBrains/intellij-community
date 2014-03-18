import abc


class A(object):
    __metaclass__ = abc.ABCMeta

    @abc.abstractproperty
    def foo(self):
        pass


class C(A):
    foo = 'bar'