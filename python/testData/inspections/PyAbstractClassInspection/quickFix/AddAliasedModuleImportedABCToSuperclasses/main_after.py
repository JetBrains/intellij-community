import abc as a

class A1(metaclass=a.ABCMeta):
    @a.abstractmethod
    def foo(self):
        pass

class A2(A1, a.ABC):
    pass
