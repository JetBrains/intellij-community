from abc import abstractmethod, ABCMeta


class A(metaclass=ABCMeta):
    @abstractmethod
    def meth(self):
        ...