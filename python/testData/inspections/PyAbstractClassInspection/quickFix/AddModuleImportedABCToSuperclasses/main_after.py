import abc

class A1(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def foo(self):
        pass


class A2(A1, abc.ABC):
    pass
