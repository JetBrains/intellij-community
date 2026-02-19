import abc
from abc import ABC


class A(ABC):
    @abc.abstractmethod
    def meth(self):
        ...