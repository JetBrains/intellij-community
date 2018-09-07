import abc

class A1(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def m1(self):
        pass