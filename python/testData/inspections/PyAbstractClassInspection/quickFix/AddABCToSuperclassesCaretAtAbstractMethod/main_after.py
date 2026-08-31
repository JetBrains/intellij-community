from abc import abstractmethod, ABC


class A(ABC):
    @abstractmethod
    def meth(self):
        ...