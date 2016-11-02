import abc


class A(metaclass=abc.ABCMeta):
    <warning descr="'abc.abstractproperty' is deprecated since Python 3.3. Use 'property' with 'abc.abstractmethod' instead.">@abc.abstractproperty</warning>
    def prop(self):
        pass