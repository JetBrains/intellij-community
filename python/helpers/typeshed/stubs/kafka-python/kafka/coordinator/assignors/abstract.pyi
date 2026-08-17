import abc
from enum import IntEnum

class RebalanceProtocol(IntEnum):
    EAGER = 0
    COOPERATIVE = 1

class AbstractPartitionAssignor(metaclass=abc.ABCMeta):
    @property
    @abc.abstractmethod
    def name(self): ...
    def supported_protocols(self) -> list[RebalanceProtocol]: ...
    @abc.abstractmethod
    def assign(self, cluster, members): ...
    @abc.abstractmethod
    def metadata(self, topics): ...
    @abc.abstractmethod
    def on_assignment(self, assignment, generation): ...
