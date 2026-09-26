from abc import ABC, ABCMeta


class AbstractClass1(ABC):
    def foo(self) -> None: ...


c1 = <weak_warning descr="Cannot instantiate abstract class 'AbstractClass1'">AbstractClass1()</weak_warning>


class ConcreteClass1(AbstractClass1):
    ...


cc1 = ConcreteClass1()


class AbstractClass2(metaclass=ABCMeta):
    ...


c2 = <weak_warning descr="Cannot instantiate abstract class 'AbstractClass2'">AbstractClass2()</weak_warning>


class ConcreteClass2(AbstractClass2):
    def bar(self) -> None: ...


cc2 = ConcreteClass2()
