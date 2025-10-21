import abc


class A(metaclass=abc.ABCMeta):
    @abc.<warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">abstractproperty</warning>
    def prop(self):
        pass


from abc import <warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">abstractproperty</warning>


class B(metaclass=abc.ABCMeta):
    @<warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">abstractproperty</warning>
    def prop(self):
        pass


from abc import <warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">abstractproperty</warning> as ap


class C(metaclass=abc.ABCMeta):
    @<warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">ap</warning>
    def prop(self):
        pass


import abc as foo


class D(metaclass=abc.ABCMeta):
    @foo.<warning descr="Deprecated since Python 3.3. Use `@property` stacked on top of `@abstractmethod` instead.">abstractproperty</warning>
    def prop(self):
        pass


class A:
    @abc.<warning descr="Deprecated since Python 3.3. Use `@classmethod` stacked on top of `@abstractmethod` instead.">abstractclassmethod</warning>
    def foo(cls):
        pass

    @abc.<warning descr="Deprecated since Python 3.3. Use `@staticmethod` stacked on top of `@abstractmethod` instead.">abstractstaticmethod</warning>
    def bar():
        pass