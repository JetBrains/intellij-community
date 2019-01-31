from abc import object, ABCMeta, abstractmethod


class Parent(object, metaclass=ABCMeta):
    @abstractmethod
    def my_method(self, foo):
        """
        Eats eggs
        :param foo: eggs
        """
        pass

    @classmethod
    @abstractmethod
    def my_class_method():
        pass