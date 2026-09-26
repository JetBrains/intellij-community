import abc
from _typeshed import Incomplete

class AbstractType(metaclass=abc.ABCMeta):
    @classmethod
    @abc.abstractmethod
    def encode(cls, value): ...
    @classmethod
    @abc.abstractmethod
    def decode(cls, data): ...
    repr: Incomplete
