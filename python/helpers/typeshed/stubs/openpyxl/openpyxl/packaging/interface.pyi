from abc import ABC, ABCMeta, abstractmethod

class ISerialisableFile(ABC, metaclass=ABCMeta):
    @property
    @abstractmethod
    def id(self): ...
