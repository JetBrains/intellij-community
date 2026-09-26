import abc


class A(abc.ABC):
    @abc.abstractmethod
    def meth(self):
        ...