from abc import ABC, abstractmethod

class A1(ABC):
    @abstractmethod
    def m1(self):
        pass

class A<caret>2(A1):
    pass