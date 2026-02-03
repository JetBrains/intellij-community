from abc import ABC, abstractproperty


class Base(ABC):
    @abstractproperty
    def some_method(self):
        pass


class <weak_warning descr="Class Sub must implement all abstract methods">S<caret>ub</weak_warning>(Base):
    pass