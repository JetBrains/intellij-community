from abc import abstractmethod, ABCMeta


class A:
    <weak_warning descr="Abstract methods are allowed in classes whose metaclass is 'ABCMeta'">@abstractmethod</weak_warning>
    def foo(self): ...


class AbstractClassA(metaclass=ABCMeta):
    ...


class AbstractClassB(AbstractClassA):
    @abstractmethod
    def foo(self): ...


class AbstractClassC(AbstractClassB):
    @abstractmethod
    def bar(self): ...


class AbstractClassD(AbstractClassC):
    def foo(self): ...
    def bar(self): ...
    @abstractmethod
    def buz(self): ...
