from abc import ABCMeta, abstractmethod


class Spam(metaclass=ABCMeta):
    @abstractmethod
    def __add__(self, other):
        pass

    __radd__ = __add__


class C(Spam):
    def __add__(self, other):
        pass
#
#
