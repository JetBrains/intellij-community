from abc import ABCMeta, abstractmethod


class Parent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def my_method(self, foo):
        pass

    @abstractmethod
    def my_method_2(self, foo):
        pass