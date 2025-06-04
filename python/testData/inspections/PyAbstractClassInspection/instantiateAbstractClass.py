from abc import abstractmethod, ABCMeta


class AbstractClassA(metaclass=ABCMeta):
    @abstractmethod
    def foo(self): ...

    @abstractmethod
    def bar(self): ...


a = <warning descr="Cannot instantiate abstract class 'AbstractClassA'">AbstractClassA()</warning>


class <weak_warning descr="Class AbstractClassB must implement all abstract methods">AbstractClassB</weak_warning>(AbstractClassA):
    ...


b = <warning descr="Cannot instantiate abstract class 'AbstractClassB'">AbstractClassB()</warning>


class <weak_warning descr="Class AbstractClassC must implement all abstract methods">AbstractClassC</weak_warning>(AbstractClassB):
    def foo(self): ...


c = <warning descr="Cannot instantiate abstract class 'AbstractClassC'">AbstractClassC()</warning>


class ConcreteClassC(AbstractClassC):
    def bar(self): ...


cc = ConcreteClassC()
