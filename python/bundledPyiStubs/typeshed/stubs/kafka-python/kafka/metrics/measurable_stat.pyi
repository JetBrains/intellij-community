import abc

from kafka.metrics.measurable import AbstractMeasurable
from kafka.metrics.stat import AbstractStat

class AbstractMeasurableStat(AbstractStat, AbstractMeasurable, metaclass=abc.ABCMeta): ...
