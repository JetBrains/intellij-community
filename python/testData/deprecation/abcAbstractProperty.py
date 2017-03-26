import abc


class A(metaclass=abc.ABCMeta):
    <warning descr="'abc.abstractproperty' is deprecated since Python 3.3. Use 'property' with 'abc.abstractmethod' instead.">@abc.abstractproperty</warning>
    def prop(self):
        pass


from abc import abstractproperty


class B(metaclass=abc.ABCMeta):
    <warning descr="'abc.abstractproperty' is deprecated since Python 3.3. Use 'property' with 'abc.abstractmethod' instead.">@abstractproperty</warning>
    def prop(self):
        pass


from abc import abstractproperty as ap


class C(metaclass=abc.ABCMeta):
    <warning descr="'abc.abstractproperty' is deprecated since Python 3.3. Use 'property' with 'abc.abstractmethod' instead.">@ap</warning>
    def prop(self):
        pass


import abc as foo


class D(metaclass=abc.ABCMeta):
    <warning descr="'abc.abstractproperty' is deprecated since Python 3.3. Use 'property' with 'abc.abstractmethod' instead.">@foo.abstractproperty</warning>
    def prop(self):
        pass