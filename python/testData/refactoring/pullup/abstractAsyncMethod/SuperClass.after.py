from abc import ABCMeta, abstractmethod


class Parent(metaclass=ABCMeta):
    @abstractmethod
    async def async_method(self):
        """An async method that should be pulled up"""
        pass