import abc

class A1(abc.ABC):
    @abc.abstractmethod
    def m1(self):
        pass

class A2(A1, abc.ABC):
    pass