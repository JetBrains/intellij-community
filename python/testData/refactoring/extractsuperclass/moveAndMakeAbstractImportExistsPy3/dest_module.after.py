from abc import ABCMeta
from abc import abstractmethod


ABCMeta()
abstractmethod()


class NewParent(object, metaclass=ABCMeta):
    @classmethod
    @abstractmethod
    def foo_method(cls):
        pass