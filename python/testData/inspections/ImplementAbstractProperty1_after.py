from abc import ABC, abstractproperty


class Base(ABC):
    @abstractproperty
    def some_method(self):
        pass


class Sub(Base):
    @property
    def some_method(self):
        pass

