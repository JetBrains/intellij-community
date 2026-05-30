import abc
from _typeshed import Incomplete

class AbstractType(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def encode(cls, value): ...
    @abc.abstractmethod
    def decode(cls, data): ...
    repr: Incomplete
