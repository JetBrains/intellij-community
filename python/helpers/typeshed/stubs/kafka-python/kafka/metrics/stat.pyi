import abc

class AbstractStat(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def record(self, config, value, time_ms): ...
