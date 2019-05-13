from abc import ABCMeta, abstractmethod

class A1(metaclass=ABCMeta):
    @abstractmethod
    def m1(self):
        pass

class A<caret>2(A1):
    pass