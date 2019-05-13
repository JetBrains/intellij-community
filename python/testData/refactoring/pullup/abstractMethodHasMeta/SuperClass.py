from abc import ABCMeta, abstractmethod


class Parent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def my_method2(self):
        pass