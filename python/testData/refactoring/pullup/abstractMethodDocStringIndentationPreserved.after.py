from abc import ABCMeta, abstractmethod


class A:
    __metaclass__ = ABCMeta

    @abstractmethod
    def m(self, x):
        """
        Parameters:
            x (int): number
        """
        pass


class B(A):
    def m(self, x):
        """
        Parameters:
            x (int): number
        """
        return x
