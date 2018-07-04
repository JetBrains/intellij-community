from abc import ABCMeta, abstractmethod

class BaseClass(metaclass=ABCMeta):
    @property
    @abstractmethod
    def abstract_property(self):
        pass

class SubClass(BaseClass):
    abstract_property = 5

class SubSubClass(SubClass):
    pass