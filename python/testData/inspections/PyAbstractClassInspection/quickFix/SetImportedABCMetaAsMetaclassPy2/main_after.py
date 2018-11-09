from abc import ABCMeta, abstractmethod

class A1:

    __metaclass__ = ABCMeta

    @abstractmethod
    def m1(self):
        pass

class A2(A1):
    __metaclass__ = ABCMeta