import abc

from kafka.metrics.stat import AbstractStat

class AbstractCompoundStat(AbstractStat, metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def stats(self): ...

class NamedMeasurable:
    __slots__ = ("_name", "_stat")
    def __init__(self, metric_name, measurable_stat) -> None: ...
    @property
    def name(self): ...
    @property
    def stat(self): ...
