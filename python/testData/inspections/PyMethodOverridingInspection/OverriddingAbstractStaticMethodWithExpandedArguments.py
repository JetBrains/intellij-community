from abc import ABC, abstractmethod


class Abstract(ABC):
    @staticmethod
    @abstractmethod
    def bar(*args, **kwargs):
        pass


class Child(Abstract):
    @staticmethod
    def bar(arg_one, arg_two, arg_three):
        pass