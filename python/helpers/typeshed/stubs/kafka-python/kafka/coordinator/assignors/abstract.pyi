import abc
from _typeshed import Incomplete

log: Incomplete

class AbstractPartitionAssignor(metaclass=abc.ABCMeta):
    @property
    @abc.abstractmethod
    def name(self): ...
    @abc.abstractmethod
    def assign(self, cluster, members): ...
    @abc.abstractmethod
    def metadata(self, topics): ...
    @abc.abstractmethod
    def on_assignment(self, assignment): ...
