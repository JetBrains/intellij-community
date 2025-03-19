import abc


class A(metaclass=abc.ABCMeta):
    @abc.<warning descr="Use 'property' with 'abstractmethod' instead">abstractproperty</warning>
    def prop(self):
        pass


from abc import <warning descr="Use 'property' with 'abstractmethod' instead">abstractproperty</warning>


class B(metaclass=abc.ABCMeta):
    @<warning descr="Use 'property' with 'abstractmethod' instead">abstractproperty</warning>
    def prop(self):
        pass


from abc import <warning descr="Use 'property' with 'abstractmethod' instead">abstractproperty</warning> as ap


class C(metaclass=abc.ABCMeta):
    @<warning descr="Use 'property' with 'abstractmethod' instead">ap</warning>
    def prop(self):
        pass


import abc as foo


class D(metaclass=abc.ABCMeta):
    @foo.<warning descr="Use 'property' with 'abstractmethod' instead">abstractproperty</warning>
    def prop(self):
        pass


class A:
    @abc.<warning descr="Use 'classmethod' with 'abstractmethod' instead">abstractclassmethod</warning>
    def foo(cls):
        pass

    @abc.<warning descr="Use 'staticmethod' with 'abstractmethod' instead">abstractstaticmethod</warning>
    def bar():
        pass