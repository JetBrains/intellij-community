from abc import ABCMeta
from abc import abstractmethod


ABCMeta()
abstractmethod()


class NewParent(metaclass=ABCMeta):
    @classmethod
    @abstractmethod
    def foo_method(cls):
        pass